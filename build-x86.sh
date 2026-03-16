#!/bin/bash

# 设置目标架构为 x86_64 (服务器通用架构)
PLATFORM="linux/amd64"
VERSION="latest"
OUTPUT_FILE="turtle-images.tar"

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 清理可能存在的旧 builder，避免干扰
docker buildx rm turtle-builder > /dev/null 2>&1 || true

echo -e "${YELLOW}提示: 请确保已在 Docker Desktop 设置中配置了代理 (Settings -> Resources -> Proxies)${NC}"
echo -e "${YELLOW}      HTTP/HTTPS Proxy: http://127.0.0.1:7897${NC}"
echo ""

# 1. Java 编译
echo -e "${GREEN}>>> 1. 开始 Maven 编译...${NC}"
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}Maven 编译失败，请检查代码错误${NC}"
    exit 1
fi

# 2. Docker 构建 (x86_64)
echo -e "${GREEN}>>> 2. 开始构建 Docker 镜像 ($PLATFORM)...${NC}"

build_image() {
    local service=$1
    local dockerfile=$2
    echo -e "正在构建 $service..."
    
    # 使用默认 builder，配合 --load 参数确保镜像加载到本地
    # 这样 Docker Desktop 的全局代理设置就会生效
    docker buildx build --platform $PLATFORM -f $dockerfile -t $service:$VERSION --load .
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}构建失败: $service${NC}"
        echo -e "${YELLOW}请检查 Docker Desktop 是否已配置代理，或者尝试更换网络${NC}"
        exit 1
    fi
}

# 依次构建
build_image "novel-gateway" "Dockerfile.gateway"
build_image "novel-user-service" "Dockerfile.user-service"
build_image "novel-book-service" "Dockerfile.book-service"
build_image "novel-search-service" "Dockerfile.search-service"
build_image "novel-ai-service" "Dockerfile.ai-service"

# 3. 导出镜像
echo -e "${GREEN}>>> 3. 正在导出镜像到 $OUTPUT_FILE (这可能需要几分钟)...${NC}"

docker save -o $OUTPUT_FILE \
    novel-gateway:$VERSION \
    novel-user-service:$VERSION \
    novel-book-service:$VERSION \
    novel-search-service:$VERSION \
    novel-ai-service:$VERSION

echo -e "${GREEN}>>> 成功！请将 $OUTPUT_FILE 上传到服务器${NC}"
