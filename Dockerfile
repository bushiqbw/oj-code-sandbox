#Fixme
#先将oj-code-sandbox-0.0.1-SNAPSHOT.jar.jar移至Linux系统下的/app目录下，然后执行mv oj-code-sandbox-0.0.1-SNAPSHOT.jar.jar oj-code-sandbox.jar命令
# 基础镜像
FROM openjdk:8
#指定工作目录
VOLUME /sandbox
# 复制主机jar包至镜像内，复制的目录需放置在 Dockerfile 文件同级目录下
ADD oj-code-sandbox.jar oj-code-sandbox.jar
# 容器启动执行命令
ENTRYPOINT ["java","-jar", "/oj-code-sandbox.jar" ]
# 对外暴露的端口号
EXPOSE  8090
