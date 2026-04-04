#!/bin/bash
# 一键部署脚本（本地执行）
# 功能：上传镜像包 → 停止旧容器 → 删除旧镜像 → 加载新镜像 → 启动服务
#
# 用法:
#   ./deploy.sh              # 部署前端 + 后端（默认）
#   ./deploy.sh --backend    # 只部署后端
#   ./deploy.sh --frontend   # 只部署前端

# ==================== 配置区域（首次使用请修改这里） ====================
SERVER_HOST="120.53.89.103"                                                 # 服务器 IP 或域名，例如: 192.168.1.100
SERVER_USER="ubuntu"                                           # SSH 用户名
SERVER_PATH="/home/ubuntu/project"                              # 服务器上的部署目录
SSH_KEY=""                                       # SSH 私钥路径（留空则使用 ssh-agent 或密码登录）

BACKEND_TAR="turtle-images.tar"                                # 后端镜像包（build-x86.sh 生成）
FRONTEND_TAR="../turtle-website-front/turtle-front-images.tar" # 前端镜像包（build-front-x86.sh 生成）
COMPOSE_FILE="docker-compose.prod.yml"
# ==================== 配置区域结束 ====================

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

log_info()  { echo -e "  ${GREEN}✔${NC}  $*"; }
log_warn()  { echo -e "  ${YELLOW}⚠${NC}  $*"; }
log_error() { echo -e "  ${RED}✘${NC}  $*"; }
log_step()  { echo -e "\n${BOLD}${BLUE}▶ $*${NC}"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ─── 解析参数 ───
DEPLOY_BACKEND=false
DEPLOY_FRONTEND=false

if [ $# -eq 0 ]; then
    DEPLOY_BACKEND=true
    DEPLOY_FRONTEND=true
else
    for arg in "$@"; do
        case $arg in
            --backend|-b)  DEPLOY_BACKEND=true ;;
            --frontend|-f) DEPLOY_FRONTEND=true ;;
            --all|-a)      DEPLOY_BACKEND=true; DEPLOY_FRONTEND=true ;;
            *)
                log_error "未知参数: $arg"
                echo "  用法: $0 [--backend|-b] [--frontend|-f] [--all|-a（默认）]"
                exit 1 ;;
        esac
    done
fi

# ─── 校验配置 ───
if [ -z "$SERVER_HOST" ]; then
    log_error "请先在脚本顶部设置 SERVER_HOST（服务器地址）"
    exit 1
fi

# ─── 构建 SSH/SCP 选项（使用 ControlMaster 复用连接，只需输一次密码） ───
CONTROL_SOCKET="/tmp/ssh-deploy-${SERVER_USER}@${SERVER_HOST}"
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=15"
SSH_OPTS="$SSH_OPTS -o ControlMaster=auto -o ControlPath=${CONTROL_SOCKET} -o ControlPersist=10m"

EXPANDED_KEY="${SSH_KEY/#\~/$HOME}"
if [ -n "$SSH_KEY" ] && [ -f "$EXPANDED_KEY" ]; then
    SSH_OPTS="$SSH_OPTS -i $EXPANDED_KEY"
fi

# 脚本退出时自动关闭 SSH 复用连接
cleanup() {
    ssh -O exit -o ControlPath="${CONTROL_SOCKET}" "${SERVER_USER}@${SERVER_HOST}" 2>/dev/null || true
}
trap cleanup EXIT

ssh_run() { ssh $SSH_OPTS "${SERVER_USER}@${SERVER_HOST}" "$@"; }
scp_put()  { scp $SSH_OPTS "$1" "${SERVER_USER}@${SERVER_HOST}:$2"; }

# ─── 预先建立 SSH 主连接（只在这里触发密码输入） ───
log_step "连接服务器"
echo -e "  正在连接 ${SERVER_USER}@${SERVER_HOST} ..."
if ! ssh $SSH_OPTS -o ControlMaster=yes -f -N "${SERVER_USER}@${SERVER_HOST}" 2>/dev/null; then
    # 如果已有复用连接则忽略错误，直接测试连通性
    if ! ssh_run true 2>/dev/null; then
        log_error "无法连接服务器 ${SERVER_HOST}，请检查地址、用户名或密码"
        exit 1
    fi
fi
log_info "连接成功，后续操作无需再输密码"

# ─── 打印 Banner ───
echo ""
echo -e "${BOLD}${BLUE}╔══════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║          一键部署脚本                ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════╝${NC}"
DEPLOY_TARGET=""
[ "$DEPLOY_BACKEND" = true ]  && DEPLOY_TARGET+="后端 "
[ "$DEPLOY_FRONTEND" = true ] && DEPLOY_TARGET+="前端"
echo -e "  服务器: ${SERVER_USER}@${SERVER_HOST}"
echo -e "  目录:   ${SERVER_PATH}"
echo -e "  范围:   ${DEPLOY_TARGET}"

# ─── 步骤 1: 创建服务器目录 ───
log_step "步骤 1/4  准备服务器目录"
ssh_run "mkdir -p ${SERVER_PATH}"
log_info "目录就绪: ${SERVER_PATH}"

# ─── 步骤 2: 上传文件 ───
log_step "步骤 2/4  上传镜像包到服务器"

if [ "$DEPLOY_BACKEND" = true ]; then
    BACKEND_TAR_PATH="${SCRIPT_DIR}/${BACKEND_TAR}"
    if [ ! -f "$BACKEND_TAR_PATH" ]; then
        log_error "未找到后端镜像包: $BACKEND_TAR_PATH"
        log_warn  "请先执行 ./build-x86.sh 构建后端镜像"
        exit 1
    fi
    SIZE=$(du -sh "$BACKEND_TAR_PATH" | cut -f1)
    echo -e "  上传后端镜像包 (${SIZE}) ..."
    scp_put "$BACKEND_TAR_PATH" "${SERVER_PATH}/${BACKEND_TAR}"
    log_info "后端镜像包上传完成"
fi

if [ "$DEPLOY_FRONTEND" = true ]; then
    FRONTEND_TAR_PATH="${SCRIPT_DIR}/${FRONTEND_TAR}"
    if [ ! -f "$FRONTEND_TAR_PATH" ]; then
        log_error "未找到前端镜像包: $FRONTEND_TAR_PATH"
        log_warn  "请先执行 ../turtle-website-front/build-front-x86.sh 构建前端镜像"
        exit 1
    fi
    SIZE=$(du -sh "$FRONTEND_TAR_PATH" | cut -f1)
    echo -e "  上传前端镜像包 (${SIZE}) ..."
    scp_put "$FRONTEND_TAR_PATH" "${SERVER_PATH}/turtle-front-images.tar"
    log_info "前端镜像包上传完成"
fi

echo -e "  上传 ${COMPOSE_FILE} ..."
scp_put "${SCRIPT_DIR}/${COMPOSE_FILE}" "${SERVER_PATH}/${COMPOSE_FILE}"
log_info "compose 文件上传完成"

# ─── 步骤 3 & 4: 远端操作（停容器 → 删镜像 → 加载 → 启动） ───
log_step "步骤 3/4  停止旧容器并删除旧镜像"
log_step "步骤 4/4  加载新镜像并启动服务"
echo ""

# 构建在服务器上执行的脚本（双引号内的 ${...} 在本地展开，单引号内的 $... 在远端展开）
REMOTE_SCRIPT="
set -e

# 兼容 docker compose / docker-compose 两种调用方式
compose() {
    if docker compose version >/dev/null 2>&1; then
        docker compose \"\$@\"
    else
        docker-compose \"\$@\"
    fi
}
COMPOSE_FILE=\"${SERVER_PATH}/${COMPOSE_FILE}\"
"

# 停止并删除容器
if [ "$DEPLOY_BACKEND" = true ] && [ "$DEPLOY_FRONTEND" = true ]; then
    REMOTE_SCRIPT+='
echo ">>> 停止并删除所有容器..."
compose -f "$COMPOSE_FILE" stop  2>/dev/null || true
compose -f "$COMPOSE_FILE" rm -f 2>/dev/null || true
'
elif [ "$DEPLOY_BACKEND" = true ]; then
    REMOTE_SCRIPT+='
echo ">>> 停止并删除后端容器..."
for svc in novel-gateway novel-user-service novel-book-service novel-search-service novel-ai-service; do
    compose -f "$COMPOSE_FILE" stop  "$svc" 2>/dev/null || true
    compose -f "$COMPOSE_FILE" rm -f "$svc" 2>/dev/null || true
done
'
elif [ "$DEPLOY_FRONTEND" = true ]; then
    REMOTE_SCRIPT+='
echo ">>> 停止并删除前端容器..."
compose -f "$COMPOSE_FILE" stop  front 2>/dev/null || true
compose -f "$COMPOSE_FILE" rm -f front 2>/dev/null || true
'
fi

# 删除旧镜像
if [ "$DEPLOY_BACKEND" = true ]; then
    REMOTE_SCRIPT+='
echo ">>> 删除旧后端镜像..."
for img in novel-gateway novel-user-service novel-book-service novel-search-service novel-ai-service; do
    docker rmi "${img}:latest" 2>/dev/null || true
done
'
fi

if [ "$DEPLOY_FRONTEND" = true ]; then
    REMOTE_SCRIPT+='
echo ">>> 删除旧前端镜像..."
docker rmi "turtle-website-front:latest" 2>/dev/null || true
'
fi

# 加载新镜像
if [ "$DEPLOY_BACKEND" = true ]; then
    REMOTE_SCRIPT+="
echo '>>> 加载新后端镜像...'
docker load -i \"${SERVER_PATH}/${BACKEND_TAR}\"
echo '    ✔ 后端镜像加载完成'
"
fi

if [ "$DEPLOY_FRONTEND" = true ]; then
    REMOTE_SCRIPT+="
echo '>>> 加载新前端镜像...'
docker load -i \"${SERVER_PATH}/turtle-front-images.tar\"
echo '    ✔ 前端镜像加载完成'
"
fi

# 启动服务
if [ "$DEPLOY_BACKEND" = true ] && [ "$DEPLOY_FRONTEND" = true ]; then
    REMOTE_SCRIPT+='
echo ""
echo ">>> 启动全部服务..."
compose -f "$COMPOSE_FILE" up -d
'
elif [ "$DEPLOY_BACKEND" = true ]; then
    REMOTE_SCRIPT+='
echo ""
echo ">>> 启动后端服务..."
compose -f "$COMPOSE_FILE" up -d novel-gateway novel-user-service novel-book-service novel-search-service novel-ai-service
'
elif [ "$DEPLOY_FRONTEND" = true ]; then
    REMOTE_SCRIPT+='
echo ""
echo ">>> 启动前端服务..."
compose -f "$COMPOSE_FILE" up -d front
'
fi

REMOTE_SCRIPT+='
echo ""
echo ">>> 当前运行中的容器:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "novel|turtle|NAME" || docker ps
'

echo "$REMOTE_SCRIPT" | ssh $SSH_OPTS "${SERVER_USER}@${SERVER_HOST}" bash

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${BOLD}${GREEN}╔══════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${GREEN}║           ✅  部署成功！             ║${NC}"
    echo -e "${BOLD}${GREEN}╚══════════════════════════════════════╝${NC}"
    echo ""
    echo "常用运维命令（在服务器上执行）:"
    echo "  查看所有日志:  docker compose -f ${SERVER_PATH}/${COMPOSE_FILE} logs -f"
    echo "  查看某服务日志: docker compose -f ${SERVER_PATH}/${COMPOSE_FILE} logs -f novel-book-service"
    echo "  查看状态:     docker compose -f ${SERVER_PATH}/${COMPOSE_FILE} ps"
    echo "  停止全部:     docker compose -f ${SERVER_PATH}/${COMPOSE_FILE} down"
    echo "  重启某服务:   docker compose -f ${SERVER_PATH}/${COMPOSE_FILE} restart <服务名>"
    echo ""
else
    echo ""
    log_error "部署失败，请检查以上错误信息"
    exit 1
fi
