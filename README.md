# RuoYi-Cloud 微服务 CI/CD 与 K8s 交付 SOP

基于 RuoYi-Cloud 3.8.5 微服务框架，从零搭建一条完整的容器化交付链路：

`GitLab 代码托管` → `Jenkins 自动构建` → `Harbor 私有镜像仓库` → `Kubernetes 滚动发布`

整套多节点环境为手动搭建，过程中沉淀的配置、脚本与报错记录都整理在这个仓库里。

---

## 一、这个仓库解决什么问题

微服务架构下（网关 + 认证 + 系统 + 业务模块），手工发布有几个绕不开的麻烦：

- 模块多，逐个打包、上传、重启，一次发布要几十分钟，且容易漏步骤
- 镜像没有统一版本管理，出问题想回滚只能靠翻记录
- 多环境（测试 / 生产）配置混在代码里，发布时容易带错配置
- 服务起不来时缺少快速定位手段，只能一个个节点登上去看

这个仓库把上面四个问题对应的解决方案都落成了可执行的配置和脚本：WebHook 驱动的自动构建、带版本号的镜像推送、K8s 滚动更新与一键回滚、以及完整的排错记录。

## 二、技术链路与组件

| 环节 | 组件 | 说明 |
|---|---|---|
| 代码托管 | GitLab | 推送触发 WebHook |
| 持续集成 | Jenkins（JDK 11） | Pipeline as Code，见 `jenkinsfile/` |
| 镜像仓库 | Harbor | 私有项目、镜像清理策略 |
| 容器编排 | Kubernetes | Deployment / Service / ConfigMap / HPA / PDB |
| 监控接入 | Prometheus Operator | ServiceMonitor 自动抓取 |
| 依赖中间件 | MySQL / Redis | 业务数据与缓存 |

**节点角色规划**（内网 `192.168.44.0/24`）：

| 节点 | 角色 |
|---|---|
| `.10` | 构建 Agent（Maven + Node.js + Git） |
| `.23` | Jenkins 服务端 |
| `.24` | GitLab 服务端 |
| `.30` | Harbor 服务端 |
| 集群节点 | K8s（复用已有环境，未重新搭建） |

操作系统 CentOS 7.9 x86_64。

## 三、仓库结构

```
jenkinsfile/
  Jenkinsfile                  完整流水线，前端 + 后端构建（882 行）
  pipeline.groovy              构建阶段：拉取代码、Maven 打包、镜像构建与推送（216 行）
  stage-服务部署.groovy          部署阶段：K8s 滚动更新、健康检查、失败回滚（709 行）
k8s-manifests/
  horizontalpodautoscaler-ruoyi-gatewa.yaml   HPA 自动扩缩容
  poddisruptionbudget-ruoyi-gateway-pd.yaml   PDB 干扰预算（滚动更新时不中断）
  servicemonitor-ruoyi-gateway-monitor.yaml   Prometheus 抓取配置
scripts/                       按部署顺序编号，03 → 21
  03-安装基础依赖包.sh
  04-Docker安装-所有节点必装.sh
  05-Docker-Agent节点-192-168-44-10-部署.sh
  06-GitLab服务器-192-168-44-24-部署.sh
  07-Jenkins服务器-192-168-44-23-部署.sh
  08-Harbor服务器-192-168-44-30-部署.sh
  09-K8s集群验证-复用已有环境-无需重新搭建.sh
  11-拉取并配置ruoyi-cloud源码.sh
  14-本地代码上传到GitLab.sh
  21-应用服务层高可用.sh        多副本 + 反亲和 + 优雅启停
RuoYi-Cloud-CICD部署SOP.md     完整 SOP，含逐步截图与报错定位过程
images/                        部署过程截图 53 张
```

## 四、怎么复现

> **关于 `scripts/*.sh`**：这些是**部署过程的操作记录**，里面保留了 `[root@xxx ~]#` 提示符和命令输出，属于"照着敲"的文档，不是能直接 `bash xx.sh` 跑起来的自动化脚本。里面的命令按顺序执行即可。

1. **环境规划**：按 SOP 的硬件与角色规划表，配好各节点 IP 与角色
2. **基础环境**：`scripts/03` → `scripts/04`（所有节点装 Docker）
3. **工具链部署**：`scripts/05`（Agent）→ `06`（GitLab）→ `07`（Jenkins）→ `08`（Harbor）
4. **集群验证**：`scripts/09`，确认 K8s 可用
5. **业务接入**：`scripts/11` 拉取源码 → `scripts/14` 推到 GitLab
6. **配置流水线**：把 `jenkinsfile/` 下的内容配进 Jenkins 任务，代码托管凭据绑定好
7. **触发构建**：观察 Harbor 镜像推送与 K8s 滚动发布

**凭据说明**：数据库与 Redis 口令走 Jenkins 凭据绑定（`credentials('ruoyi-mysql-pwd')`），
代码库里不落明文。K8s 侧的中间件凭据走 Secret 引用。

## 五、完整文档

逐步操作、每步的截图与报错定位，都在 [`RuoYi-Cloud-CICD部署SOP.md`](./RuoYi-Cloud-CICD部署SOP.md)。
