# 基础镜像：选择与本地 Tomcat 兼容的 JDK 基础镜像（如本地 Tomcat 用 JDK 11，基础镜像也用 JDK 11）
# 推荐使用 openjdk 官方镜像，轻量且稳定
FROM openjdk:11-jre-slim

# 维护者信息（可选）
LABEL maintainer="tongguoshan <guoshan.tong@landingbj.com>"

# 定义 Tomcat 安装目录（容器内路径，与本地一致便于维护）
ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH

# 创建容器内 Tomcat 目录
RUN mkdir -p $CATALINA_HOME

# 将本地复制的 Tomcat 目录（./tomcat-local）复制到容器内的 /usr/local/tomcat
COPY /tomcat-local $CATALINA_HOME

# 给 Tomcat 启动脚本添加执行权限（避免容器内权限不足）
RUN chmod +x $CATALINA_HOME/bin/*.sh

# 暴露 Tomcat 端口（与本地 Tomcat 一致，如 8080 主端口、8009 AJP 端口）
EXPOSE 8080
EXPOSE 8009

# 容器启动时执行 Tomcat 启动脚本（前台运行，确保容器不退出）
CMD ["catalina.sh", "run"]

#docker run -d -p 8082:8080 --name linkmind-webapp-v1 192.168.200.26/landingbj/linkmind-webapp:v1.0
