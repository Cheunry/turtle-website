更新代码至github仓库:
git add .
git commit -m "消息备注"
git push origin main


新增忽略
# 1. 从 Git 版本控制中移除该目录，但保留本地文件
git rm -r --cached doc/docker/

# 2. 将 .gitignore 的修改和删除记录添加到暂存区
git add .gitignore

# 3. 提交更改
git commit -m "chore: stop tracking doc/docker/ directory but keep local files"