#!/bin/bash
# Ruoyi-cloud-cicd — 本地代码上传到GitLab
# 由 SOP 中对应章节的命令整理而成（含执行输出示例）

# --------------------------------------------------------------------
# 本地代码上传到GitLab
# --------------------------------------------------------------------
# 打开CMD，进入本地ruoyi-cloud项目目录（D:\project\ruoyi-cloud）
cd D:\project\ruoyi-cloud

# 初始化git仓库（若未初始化）
git init

# 添加所有文件到暂存区
git add .

# 提交到本地仓库
git commit -m "初始化ruoyi-cloud源码，本地验证通过"

# 关联GitLab远程仓库（替换下面的地址为自己GitLab项目的地址）
git remote add origin http://192.168.44.24/root/ruoyi-cloud.git

# 拉取远程仓库（首次上传，避免冲突）
git pull origin main --allow-unrelated-histories

# 推送本地代码到GitLab远程仓库（main分支）
git push -u origin main
