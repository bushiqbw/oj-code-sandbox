# oj-code-sandbox
### 两种实现方式
- Docker + 命令行 docker exec ...
- 命令行 (example:  java -Xmx128m -Dfile.encoding=UTF-8 -cp  Main )

### 代码沙箱的实现语言
- C++
- C
- JAVA
- Python

  
### 安全管理
- 不同语言对应的黑名单目录Word_List
- docker容器创建时指定hostConfig配置安全性
- JavaManager权限管理类

### 设计模式
- 策略模式
- 模板方法模式

### 支持自定义输入输出和系统输入输出双模式

### 远程调用方法
- 指定AUTH_REQUEST_HEADER
- 指定AUTH_REQUEST_SECRET
