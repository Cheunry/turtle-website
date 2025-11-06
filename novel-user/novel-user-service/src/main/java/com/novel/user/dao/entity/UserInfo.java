package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.time.LocalDateTime;

public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;

//    以下注解是MyBatis-plus注解，设置了id作为主键，采取主键自增策略，让数据库自动生成id
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

//    登录名
    private String userName;
//    登录密码-加密
    private String password;
//    加密盐值
    private String salt;
//    昵称
    private String niceName;
//    头像：存储头像图片路径或URL，所以类型为String
    private String userPhoto;
//    0-男，1-女
    private Integer sex;
//    用户账户余额（单位可选）
    private Long accountBalance;
//    0-正常
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getNiceName() {
        return niceName;
    }

    public void setNiceName(String niceName) {
        this.niceName = niceName;
    }

    public String getUserPhoto() {
        return userPhoto;
    }

    public void setUserPhoto(String userPhoto) {
        this.userPhoto = userPhoto;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public Long getAccountBalance() {
        return accountBalance;
    }

    public void setAccountBalance(Long accountBalance) {
        this.accountBalance = accountBalance;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    //    记录创建时间
    private LocalDateTime createTime;
//    最后更新时间
    private LocalDateTime updateTime;

    @Override
    public String toString() {
        return "UserInfo{" +
                "id=" + id +
                ", userName='" + userName + '\'' +
                ", password='" + password + '\'' +
                ", salt='" + salt + '\'' +
                ", niceName='" + niceName + '\'' +
                ", userPhoto='" + userPhoto + '\'' +
                ", sex=" + sex +
                ", accountBalance=" + accountBalance +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
