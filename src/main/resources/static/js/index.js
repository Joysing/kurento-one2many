/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

var ws = new WebSocket('wss://' + location.host + '/call');
var video,screenVideo;
var webRtcPeer,screenWebRtcPeer;
var name,roomName;

window.onload = function() {
	console = new Console();
	video = document.getElementById('video');
	screenVideo = document.getElementById('screenVideo');
	disableStopButton();
}

window.onbeforeunload = function() {
    console.log("页面刷新或关闭了");
	ws.close();
}

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'existingParticipants':
		if(parsedMessage.name.toString().indexOf('teacher')!==-1){
            presenter();
		}else{
			viewer();
		}

        break;
	case 'newParticipantArrived':
	    console.log(parsedMessage.name+" online");
	    break;
	case 'participantLeft':
	    console.log(parsedMessage.name+" offline");
	    break;
	case 'presenterResponse':
		presenterResponse(parsedMessage);
		break;
	case 'viewerResponse':
		viewerResponse(parsedMessage);
		break;
	case 'presenterScreenResponse':
		presenterScreenResponse(parsedMessage);
		break;
	case 'viewerScreenResponse':
		viewerScreenResponse(parsedMessage);
		break;
	case 'iceCandidate':
		webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
		break;
	case 'iceCandidateScreen':
        screenWebRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
		break;
	case 'stopCommunication':
		dispose();
		break;
	default:
		console.error('Unrecognized message', parsedMessage);
	}
}

function register() {
    name = document.getElementById('name').value;
    roomName = document.getElementById('roomName').value;

    document.getElementById('room-header').innerText = roomName+" "+name;
    document.getElementById('join').style.display = 'none';
    document.getElementById('room').style.display = 'block';

    var message = {
        id : 'joinRoom',
        name : name,
        room : roomName
    }
    sendMessage(message);
}

function presenterResponse(message) {
	if (message.response !== 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Call not accepted for the following reason: ' + errorMsg);
		dispose();
	} else {
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function presenterScreenResponse(message) {
	if (message.response !== 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Call not accepted for the following reason: ' + errorMsg);
		dispose();
	} else {
        screenWebRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function viewerResponse(message) {
	if (message.response !== 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Call not accepted for the following reason: ' + errorMsg);
		dispose();
	} else {
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});

	}
}
function viewerScreenResponse(message) {
	if (message.response !== 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Call not accepted for the following reason: ' + errorMsg);
		dispose();
	} else {
        screenWebRtcPeer.processAnswer(message.sdpAnswer, function(error) {
            if (error)
                return console.error(error);
        });
	}
}

function presenter() {
	if (!webRtcPeer) {
		showSpinner(video);
        //摄像头流
		var options = {
			localVideo : video,
			onicecandidate : onIceCandidate
		}
		webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					webRtcPeer.generateOffer(onOfferPresenter);
				});

		enableStopButton();
	}
	if (!screenWebRtcPeer) {
		showSpinner(screenVideo);
        //屏幕流
        getScreenConstraints(true,function(error, screen_constraints) {
            if (error) {
                return alert(error);
            }

            navigator.getUserMedia = navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
            navigator.getUserMedia({
                video: screen_constraints
            }, function(stream) {
                var mediaConstraints = {
                    audio: false,
                    video: {
                        width: { min: 1024, ideal: 1280, max: 1920 },
                        height: { min: 576, ideal: 720, max: 1080 },
                    }
                };
                var options = {
                    localVideo : screenVideo,
                    onicecandidate : onIceCandidateScreen,
                    mediaConstraints:mediaConstraints,
                    videoStream:stream,
                }

                screenWebRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
                    function(error) {
                        if (error) {
                            return console.error(error);
                        }
                        screenWebRtcPeer.generateOffer(onOfferPresenterScreen);
                    });
            }, function(error) {
                alert(JSON.stringify(error, null, '\t'));
            });
        });
	}
}

function onOfferPresenter(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'presenter',
        name:name,
		sdpOffer : offerSdp
	}
	sendMessage(message);
}
function onOfferPresenterScreen(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'presenterScreen',
        name:name,
		sdpOffer : offerSdp
	}
	sendMessage(message);
}

function viewer() {
	if (!webRtcPeer) {
		showSpinner(video);

		var options = {
			remoteVideo : video,
			onicecandidate : onIceCandidate
		}
		webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferViewer);
				});

		enableStopButton();
	}
	if (!screenWebRtcPeer) {
		showSpinner(screenVideo);

        var mediaConstraints = {
            audio: false,
            video: {
                width: { min: 1024, ideal: 1280, max: 1920 },
                height: { min: 576, ideal: 720, max: 1080 },
            }
        };
		var screenOptions = {
			remoteVideo : screenVideo,
			onicecandidate : onIceCandidateScreen,
            mediaConstraints:mediaConstraints
		}
        screenWebRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(screenOptions,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferViewerScreen);
				});

		enableStopButton();
	}
}

function onOfferViewer(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'viewer',
        name:name,
        room:roomName,
		sdpOffer : offerSdp
	}
	sendMessage(message);
}

function onOfferViewerScreen(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'viewerScreen',
        name:name,
        room:roomName,
		sdpOffer : offerSdp
	}
	sendMessage(message);
}

function onIceCandidate(candidate) {
	console.log("Local candidate" + JSON.stringify(candidate));

	var message = {
		id : 'onIceCandidate',
		candidate : candidate,
        name:name
    };
	sendMessage(message);
}
function onIceCandidateScreen(candidate) {
	console.log("Screen Local candidate" + JSON.stringify(candidate));

	var message = {
        id : 'onIceCandidateScreen',
		candidate : candidate,
        name:name
    };
	sendMessage(message);
}

function stop() {
	var message = {
		id : 'stop'
	}
	sendMessage(message);
	dispose();
}

function dispose() {
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;
		screenWebRtcPeer.dispose();
        screenWebRtcPeer = null;
	}
	hideSpinner(video);

	disableStopButton();
}

function disableStopButton() {
	enableButton('#presenter', 'presenter()');
	enableButton('#viewer', 'viewer()');
	disableButton('#stop');
}

function enableStopButton() {
	disableButton('#presenter');
	disableButton('#viewer');
	enableButton('#stop', 'stop()');
}

function disableButton(id) {
	$(id).attr('disabled', true);
	$(id).removeAttr('onclick');
}

function enableButton(id, functionName) {
	$(id).attr('disabled', false);
	$(id).attr('onclick', functionName);
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}

function showSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = 'center transparent url("./img/spinner.gif") no-repeat';
	}
}

function hideSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].src = '';
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});
