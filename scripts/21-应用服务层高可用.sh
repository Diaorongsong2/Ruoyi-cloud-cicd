#!/bin/bash
# Ruoyi-cloud-cicd — 应用服务层高可用
# 由 SOP 中对应章节的命令整理而成（含执行输出示例）

# --------------------------------------------------------------------
# 多副本与 Pod 反亲和部署
# --------------------------------------------------------------------
spec:
  replicas: 2
  affinity:
    podAntiAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchLabels:
            app: ruoyi-cloud-gateway
        topologyKey: kubernetes.io/hostname

# --------------------------------------------------------------------
# 优雅启停与无损发布
# --------------------------------------------------------------------
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  terminationGracePeriodSeconds: 60
  lifecycle:
    preStop:
      exec:
        command: ["/bin/sh", "-c", "sleep 20 && curl -X POST http://127.0.0.1:8080/actuator/shutdown"]
