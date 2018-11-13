基于kurento（webrtc）实现的在线视频
=====================

此系统的总体架构图：
![image](https://github.com/Joysing/kurento-one2many/blob/master/src/main/resources/static/img/kurento-media-server.png?raw=true)

实现两种功能：
1. 一对多

在这个实例有两种类型的用户：
+ 1个对等发送媒体（称之为Teacher）
+ N个对等体从Teacher接收媒体 （称之为Student）

正常情况下Pipeline（媒体管道）由1 + N个互连的WebRtcEndpoints（端点）组成。

但是我们的Teacher需要发送【摄像头】+【授课的电脑屏幕】两个视频流，

所以我们创建的Pipeline是由2+2N个互连的WebRtcEndpoints组成

逻辑图：

![image](https://github.com/Joysing/kurento-one2many/blob/master/src/main/resources/static/img/one2many.png?raw=true)

2. 多对多（群组）
+ 一个教室一个Pipeline（媒体管道）。这个Pipeline有多少个WebRtcEndpoint组成？
在前一个实例知道，一对多的Pipeline由1 + N个互连的WebRtcEndpoints组成。同时发送屏幕的话就变成了2*（1+N）个。

+ 由此可知，现在N个客户端同时发送摄像头，这N个客户端同时也接收，需要N\*N个端点，N个客户端同时发送摄像头和屏幕的话，由于摄像头和屏幕是分别发送的，所以是2*（N\*N），而不是2N*2N。

下图为N\*N个端点的逻辑图，如果是2*（N\*N）就是每个电脑再多一倍端点，
![image](https://github.com/Joysing/kurento-one2many/blob/master/src/main/resources/static/img/group.png?raw=true)


1.如何运行
---------------------

1.1 搭建 kurento-media-server（[官方安装文档](https://doc-kurento.readthedocs.io/en/stable/user/installation.html#local-installation)）
---------------------

KMS明确支持Ubuntu的两个长期支持（LTS）发行版：Ubuntu 14.04（Trusty）和Ubuntu 16.04（Xenial）。仅支持64位版本。

目前，KMS的主要开发环境是Ubuntu 16.04（Xenial）。

1.1.1 定义正在使用的Ubuntu版本。打开终端并仅复制以下命令之一：
+ 如果是 Ubuntu 14.04 (Trusty)输入

DISTRO="trusty"
+ 如果是 Ubuntu 16.04 (Xenial)输入

DISTRO="xenial"

1.1.2 将Kurento存储库添加到系统配置中。在上一步的同一终端运行这两个命令：

+ sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 5AFA7A83
+ sudo tee "/etc/apt/sources.list.d/kurento.list" >/dev/null <<EOF
deb http://ubuntu.openvidu.io/6.8.1 $DISTRO kms6
EOF

1.1.3 安装KMS：

+ sudo apt-get update
+ sudo apt-get install kurento-media-server #这行不行就试试下面的命令：
+ sudo apt-get install kurento-media-server-6.0

1.1.4 启动和停止：

+ sudo service kurento-media-server start
+ sudo service kurento-media-server stop

1.1.5 修改com.sendroid.kurento.config.Constants.java中的KURENTO_MEDIA_SERVER为kms服务器地址，默认端口是8888

1.1.6 KMS的日志消息在/var/log/kurento-media-server/。

1.2 安装TURN服务器（穿透内网和防火墙）
---------------
服务器必须有公网地址，
参考地址https://blog.csdn.net/bvngh3247/article/details/80742396

1.3 为kurento-media-server配置turn
---------------
1. 打开/etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
2. 取消注释 turnURL=\<user>:\<password>@\<serverIp>:\<serverPort>
3. 填入turn服务器相关信息
4. 重启kurento-media-server
+ sudo service kurento-media-server restart

2.相关socket接口
---------------
注意：必须有账号系统，至少包含以下字段：name(String)，account_type({TEACHER，STUDENT})。

2.1.1 加入一对多房间
+ 发送内容：{"id":"joinRoom","name":"用户名","room":"房间号"} 到 wss://{localhost}:{port}/call
+ 例子：{"id":"joinRoom","name":"joysing","room":"888"}
+ 上例的用户joysing必须在数据库里

2.1.2 加入多对多房间
+ 发送内容：{"id":"joinRoom","name":"用户名","room":"房间号"} 到 wss://{localhost}:{port}/groupcall
+ 例子：{"id":"joinRoom","name":"joysing","room":"888"}
+ 上例的用户joysing必须在数据库里

2.2 加入房间后
+ 如果用户account_type为TEACHER，此用户的浏览器会打开摄像头。
+ 如果用户account_type为STUDENT，只加载TEACHER的画面。
+ 然而，在多对多房间中，所有用户都是打开摄像头。

2.3 发送视频流到媒体服务器
+ 发送内容{id : 'presenter',
            name:'用户名',
            sdpOffer : {offerSdp}
            }到 wss://{localhost}:{port}/call（如果是多对多则发送到 wss://{localhost}:{port}/groupcall）
+ offerSdp是调用浏览器的webrtc时返回的远程对等体，[例子](https://github.com/Joysing/kurento-one2many/blob/28d0e9ffd734088e8bdb90bde003a29e4ce42d38/src/main/resources/static/js/index.js#L216)
+ 每个浏览器窗口的offerSdp都不同

2.4 从媒体服务器获得视频
+ 发送内容{id : 'viewer',
            name:'用户名',
            sdpOffer : {offerSdp}
            }到 wss://{localhost}:{port}/call（如果是多对多则发送到 wss://{localhost}:{port}/groupcall）
