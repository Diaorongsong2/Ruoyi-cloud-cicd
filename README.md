# CI-CD-K8s
## 项目简介
私有仓库，个人云原生实战完整落地SOP手册，用于运维/云原生岗位面试展示。
基于 RuoYi-Cloud 3.8.5 微服务搭建全自动化交付流水线，全程从零搭建并完整记录标准化操作流程。

## 完整技术链路
GitLab 代码托管 → Jenkins 自动构建流水线 → Harbor 私有镜像仓库 → Kubernetes 容器化部署

## 环境栈
- 操作系统：CentOS 7.9
- 编排：Kubernetes
- CI工具：Jenkins + GitLab
- 镜像仓库：Harbor
- 业务项目：RuoYi-Cloud 微服务

## 仓库文件说明
1. `RuoYi-Cloud-CICD部署SOP.md`：完整全流程操作文档（1600+行）
   - 包含集群节点架构规划表
   - 全组件分步搭建命令、配置代码（Jenkinsfile、K8s YAML、初始化脚本等全部内嵌文档内）
   - 上线、回滚、日常运维操作步骤
   - 部署踩坑故障排查解决方案
