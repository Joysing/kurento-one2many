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

var ws = new WebSocket('wss://' + location.host + '/groupcall');
var participants = {};
var participantsScreen = {};
var name;
function initName(username){
    name=username;
}
window.onbeforeunload = function () {
    ws.close();
};

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

    switch (parsedMessage.id) {
        case 'existingParticipants':
            onExistingParticipants(parsedMessage);
            break;
        case 'newParticipantArrived':
            onNewParticipant(parsedMessage);
            break;
        case 'participantLeft':
            onParticipantLeft(parsedMessage);
            break;
        case 'receiveVideoAnswer':
            receiveVideoResponse(parsedMessage);
            break;
        case 'receiveScreenAnswer':
            receiveScreenResponse(parsedMessage);
            break;
        case 'iceCandidate':
            participants[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
                if (error) {
                    console.error("Error adding candidate: " + error);
                }
            });
            break;
        case 'iceCandidateScreen':
            participantsScreen[parsedMessage.name+"-screen"].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
                if (error) {
                    console.error("Error adding candidate: " + error);
                }
            });
            break;
        default:
            console.error('Unrecognized message', parsedMessage);
    }
}

function register() {
	name = document.getElementById('name').value;
	var room = document.getElementById('roomName').value;

	document.getElementById('room-header').innerText = room+" "+name;
	document.getElementById('join').style.display = 'none';
	document.getElementById('room').style.display = 'block';

	var message = {
		id : 'joinRoom',
		name : name,
		room : room
	};
	sendMessage(message);
}

function onNewParticipant(request) {
	receiveVideo(request.name);
	receiveScreen(request.name);
}

function receiveVideoResponse(result) {
	participants[result.name].rtcPeer.processAnswer (result.sdpAnswer, function (error) {
		if (error) return console.error (error);
	});
}

function receiveScreenResponse(result) {
    participantsScreen[result.name+"-screen"].rtcPeer.processAnswer(result.sdpAnswer, function (error) {
        if (error) return console.error(error);
    });
}

function onExistingParticipants(msg) {
	var constraints = {
		audio : true,
		video : {
			mandatory : {
				maxWidth : 320,
				maxFrameRate : 15,
				minFrameRate : 15
			}
		}
	};
	console.log(name + " registered in room " + room);
	var participant = new Participant(name);
	participants[name] = participant;
	var video = participant.getVideoElement();

    var options = {
        localVideo: video,
        mediaConstraints: constraints,
        onicecandidate: participant.onIceCandidate.bind(participant)
    }
    //摄像头流
    participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
        function (error) {
            if (error) {
                return console.error(error);
            }
            this.generateOffer(participant.offerToReceiveVideo.bind(participant));
        });
    msg.data.forEach(receiveVideo);
    //屏幕流
    getScreenConstraints(true, function (error, screen_constraints) {
        if (error) {
            return alert(error);
        }

        navigator.getUserMedia = navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
        navigator.getUserMedia({
            video: screen_constraints
        }, function (stream) {
            var participantScreen = new Participant(name + "-screen");
            participantsScreen[name + "-screen"] = participantScreen;
            var screen = participantScreen.getVideoElement();

            var mediaConstraints = {
                audio: false,
                video: {
                    width: {min: 1024, ideal: 1280, max: 1920},
                    height: {min: 576, ideal: 720, max: 1080},
                }
            };
            var options = {
                localVideo: screen,
                onicecandidate: participantScreen.onIceCandidateScreen.bind(participantScreen),
                mediaConstraints: mediaConstraints,
                videoStream: stream,
            }

            participantScreen.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
                function (error) {
                    if (error) {
                        return console.error(error);
                    }
                    this.generateOffer(participantScreen.offerToReceiveScreen.bind(participantScreen));
                });
            msg.data.forEach(receiveScreen);
        }, function (error) {
            alert(JSON.stringify(error, null, '\t'));
        });
    });

}

function leaveRoom() {
    sendMessage({
        id: 'leaveRoom'
    });

    for (var key in participants) {
        participants[key].dispose();
    }
    for (var key1 in participantsScreen) {
        participantsScreen[key1].dispose();
    }

    document.getElementById('join').style.display = 'block';
    document.getElementById('room').style.display = 'none';

    ws.close();
    window.location.href="https://"+location.host;
}

function receiveVideo(sender) {
	var participant = new Participant(sender);
	participants[sender] = participant;
	var video = participant.getVideoElement();

	var options = {
      remoteVideo: video,
      onicecandidate: participant.onIceCandidate.bind(participant)
    }

	participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function (error) {
			  if(error) {
				  return console.error(error);
			  }
			  this.generateOffer (participant.offerToReceiveVideo.bind(participant));
	});;
}

function receiveScreen(sender) {
    var participantScreen = new Participant(sender+"-screen");
    participantsScreen[sender+"-screen"] = participantScreen;
    var video = participantScreen.getVideoElement();

    var options = {
        remoteVideo: video,
        onicecandidate: participantScreen.onIceCandidateScreen.bind(participantScreen)
    }

    participantScreen.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
        function (error) {
            if (error) {
                return console.error(error);
            }
            this.generateOffer(participantScreen.offerToReceiveScreen.bind(participantScreen));
        });
    ;
}

function onParticipantLeft(request) {
	console.log('Participant ' + request.name + ' left');
	var participant = participants[request.name];
	participant.dispose();
	delete participants[request.name];

	var participantScreen = participantsScreen[request.name+"-screen"];
    participantScreen.dispose();
	delete participantsScreen[request.name+"-screen"];
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}
