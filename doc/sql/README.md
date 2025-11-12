1. 初始状态下，MySQL 只需要执行 `novel.sql` 文件即可正常运行本系统
2. 只有开启 XXL-JOB 的功能，才需要执行 `xxl-job.sql` 文件
3. 只有开启 ShardingSphere-JDBC 的功能，才需要执行 `shardingsphere-jdbc.sql` 文件

由于小说数据太大，我将添加小说数据的sql语句保存在了本地。
小说数据本地路径：/Users/cheunry/Project/Java/novel_data/sql/novel_data.sql
