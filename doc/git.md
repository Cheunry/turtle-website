更新代码至github仓库:
git add .
git commit -m "消息备注"
git push origin main

git pull origin main

从提交历史中删除某文件但是保留本地文件
git rm --cached doc/docker/.env

# 1. 取消所有暂存
git reset

# 2. 检查状态确认
git status

# 3. 从git中移除.env文件（但保留本地文件）
git rm --cached doc/docker/.env

# 4. 确保.gitignore包含.env
echo ".env" >> .gitignore
echo "doc/docker/.env" >> .gitignore
