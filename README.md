# CI-CD-K8s
## 项目简介
私有仓库，个人云原生实战完整落地SOP手册。
基于 RuoYi-Cloud 3.8.5 微服务搭建**端到端自动化交付流水线**，整套环境从零手动搭建，完整沉淀标准化操作流程、配置代码、故障排坑方案。

## 完整技术链路
`GitLab 代码托管` → `Jenkins CI/CD自动构建` → `Harbor 私有镜像仓库` → `Kubernetes 容器化部署`

## 环境栈清单
- 操作系统：CentOS 7.9 x86_64
- 容器编排：Kubernetes
- 持续集成：Jenkins + GitLab WebHook
- 镜像仓库：Harbor
- 业务项目：RuoYi-Cloud 微服务（前后端分离）

## 仓库文件说明
1. **RuoYi-Cloud-CICD部署SOP.md**（核心文档，1600+行）
   - 多节点集群硬件、角色规划表格
   - 服务器初始化、内核优化、组件全套部署命令
   - 完整内嵌 Jenkinsfile、K8s Deployment/Service/Configmap YAML、Shell运维脚本
   - 微服务容器打包、自动发布、滚动更新、版本回滚完整流程
   - 全流程报错记录、定位思路、修复方案（运维排错实战）

## 项目核心能力体现
1. 独立规划并搭建多节点K8s集群，完成Java微服务容器化改造
2. 搭建WebHook驱动全自动流水线：代码提交自动打包、推送镜像、更新容器服务
3. Harbor仓库权限管理、镜像清理、私有仓库安全配置
4. 集群日常运维、发布回滚、服务异常排查、资源基础调优
