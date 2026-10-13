#!/bin/bash
# Ruoyi-cloud-cicd — Jenkins服务器(192.168.44.23)部署
# 由 SOP 中对应章节的命令整理而成（含执行输出示例）

# --------------------------------------------------------------------
# 拉取Jenkins镜像并启动容器
# --------------------------------------------------------------------
[root@Jenkins ~]# vim /etc/hosts
192.168.88.30 Jenkins
[root@Jenkins ~]# yum -y install java-11-openjdk-devel.x86_64   #安装OpenJDK11
[root@Jenkins ~]# ln -s /usr/lib/jvm/java-11-openjdk-11.0.15.0.9-2.el8_5.x86_64/ /usr/lib/jvm/jdk                                                #创建JDK环境软链接
[root@Jenkins ~]# vim /etc/bashrc 
[root@Jenkins ~]# tail -2 /etc/bashrc                           #声明JAVA_HOME环境变量
export JAVA_HOME="/usr/lib/jvm/jdk/"
export PATH=${JAVA_HOME}/bin/:$PATH
[root@Jenkins ~]# source /etc/bashrc                            #刷新当前bash环境
[root@Jenkins ~]# echo ${JAVA_HOME}                             #查看JAVA_HOME变量
/usr/lib/jvm/jdk/
[root@Jenkins ~]# which java
/usr/lib/jvm/jdk/bin/java
[root@Jenkins ~]# java -version
openjdk version "11.0.15" 2022-04-19 LTS
OpenJDK Runtime Environment 18.9 (build 11.0.15+9-LTS)
OpenJDK 64-Bit Server VM 18.9 (build 11.0.15+9-LTS, mixed mode, sharing)
[root@Jenkins ~]# 

#安装工具相关软件
[root@Jenkins ~]# yum -y install git postfix    #Git用于拉取代码、postfix用于发邮件

#安装Jenkins
[root@Jenkins ~]# ls jenkins-2.361.4-1.1.noarch.rpm 
jenkins-2.361.4-1.1.noarch.rpm
[root@Jenkins ~]# yum -y localinstall ./jenkins-2.361.4-1.1.noarch.rpm  #安装Jenkins

#启动Jenkins服务
[root@Jenkins ~]# systemctl enable jenkins.service              #设置Jenkins开机自启动
[root@Jenkins ~]# systemctl start jenkins.service               #启动Jenkins服务器
[root@Jenkins ~]# ss -antpul | grep java                        #确认8080端口被监听
tcp   LISTEN 0      50     *:8080     *:*    users:(("java",pid=13602,fd=8))
