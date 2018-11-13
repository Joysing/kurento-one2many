基于kurento（webrtc）实现的在线视频
=====================
实现两种功能：
+ 一对多
+ 多对多（群组）

此系统的总体架构图：
![]

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

1.1.5 KMS的日志消息在/var/log/kurento-media-server/。

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
