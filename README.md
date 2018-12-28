# 深入理解Tomcat

`前言`

>  学习一个优秀的框架，总要循序渐进，了解->使用->原理->源码->改造。

`材料`

1. 下载Tomcat-8.5.37 程序 https://tomcat.apache.org/download-80.cgi
2. 下载Tomcat-8.5.37 源码 http://archive.apache.org/dist/tomcat/tomcat-8/v8.5.37/src/
3.  准备调试代码,如下

```
public static void main(String args[]) throws Exception {
        Bootstrap bootstrap=new Bootstrap();
        bootstrap.start();
        Thread.sleep(Integer.MAX_VALUE);
}
```
## 目录

- ** Tomcat 组件分析 **
- ** Tomcat 生产配置(网络io模型,调优思路) **
- ** Tomcat 是如何启动的？NIO从接受socket到我们的servlet？**
- ** Tomcat 如何打破双亲委派？**


------------

### 0.简介

	Tomcat 是一个基于JAVA的WEB容器,其实现了JAVA EE中的 Servlet 与 jsp 规范,与Nginx,Apache 服务器不同在于一般用于动态请求处理;在架构设计上采用面向组件的方式设计,即整体功能是通过组件的方式拼装完成。另外每个组件都可以被替换以保证灵活性。

### 1. Tomcat 组件分析

[![](http://files.res.openread.cn/2.png)](http://files.res.openread.cn/2.png)


** 由上图可知Tomcat组成如下 **

- 一个 Server 和 多个Service
- Connector 连接器
	- HTTP 1.1
	- SSL  https
	- AJP（ Apache JServ Protocol） apache 私有协议，用于apache 反向代理Tomcat
- Container 启动引擎
	- Engine  引擎 catalina
	- Host   虚拟机 基于域名 分发请求
	- Context 隔离各个WEB应用 每个Context的  ClassLoader都是独立
- Component 
	- Manager （管理器）
	- logger （日志管理）
	- loader （载入器）
	- pipeline (管道)
	- valve （管道中的阀,filter）

** 他们的关系如下图 **

[![](http://files.res.openread.cn/4.png)](http://files.res.openread.cn/4.png)

** 也可以从server.xml文件中加深理解 **

[![](http://files.res.openread.cn/5.png)](http://files.res.openread.cn/5.png)


------------


### 2. Tomcat 生产配置(io模型,调优思路)

** 一般的部署思路 **
1. 复制WAR包至Tomcat webapp 目录。
2. 执行starut.bat 脚本启动。
3. 启动过程中war 包会被自动解压装载。

> ** 问题 **：多个项目在一起配置相互影响问题多多

** 生产环境部署策略 **

1. 实现Tomcat程序和应用部署目录部署相互分离
2. 实现各个应用相互不影响
3. 使用脚本启动

```
我们只需要在启动时指定CATALINA_HOME 与  CATALINA_BASE 参数即可实现。

启动参数--------描述说明
JAVA_OPTS--------jvm 启动参数 , 设置内存  编码等 -Xms100m -Xmx200m -Dfile.encoding=UTF-8
JAVA_HOME--------指定jdk 目录，如果未设置从java 环境变量当中去找。
CATALINA_HOME--------Tomcat 程序根目录 
CATALINA_BASE--------应用部署目录，默认为$CATALINA_HOME
CATALINA_OUT--------应用日志输出目录：默认$CATALINA_BASE/log
CATALINA_TMPDIR--------应用临时目录：默认：$CATALINA_BASE/temp
```
创建目录
```
payment
	├─webapps
	├─logs
	├─temp
	├─conf
	reload.sh
```
```
#!/bin/bash 
export JAVA_OPTS="-Xms100m -Xmx200m"
export JAVA_HOME=/root/svr/jdk/
export CATALINA_HOME=/usr/local/apache-tomcat-8.5.34
export CATALINA_BASE="`pwd`"

case $1 in
        start)
        $CATALINA_HOME/bin/catalina.sh start
                echo start success!!
        ;;
        stop)
                $CATALINA_HOME/bin/catalina.sh stop
                echo stop success!!
        ;;
        restart)
        $CATALINA_HOME/bin/catalina.sh stop
                echo stop success!!
                sleep 2
        $CATALINA_HOME/bin/catalina.sh start
        echo start success!!
        ;;
        version)
        $CATALINA_HOME/bin/catalina.sh version
        ;;
        configtest)
        $CATALINA_HOME/bin/catalina.sh configtest
        ;;
        esac
exit 0
```

** 线程模型 **

> Tomcat支持的IO模型说明

- BIO 阻塞式IO，即Tomcat使用传统的java.io进行操作。该模式下每个请求都会创建一个线程，对性能开销大，不适合高并发场景。优点是稳定，适合连接数目小且固定架构。
- NIO 非阻塞式IO，jdk1.4 之后实现的新IO。该模式基于多路复用选择器监测连接状态在通知线程处理，从而达到非阻塞的目的。比传统BIO能更好的支持并发性能。Tomcat 8.0之后默认采用该模式
- APR 全称是 Apache Portable Runtime/Apache可移植运行库)，是Apache HTTP服务器的支持库。可以简单地理解为，Tomcat将以JNI的形式调用Apache HTTP服务器的核心动态链接库来处理文件读取或网络传输操作。使用需要编译安装APR 库
- AIO 异步非阻塞式IO，jdk1.7后之支持 。与nio不同在于不需要多路复用选择器，而是请求处理线程执行完程进行回调调知，已继续执行后续操作。Tomcat 8之后支持。

** 使用指定IO模型的配置方式 **

```
配置 server.xml 文件当中的 <Connector  protocol="HTTP/1.1">    修改即可。
默认配置 8.0  protocol=“HTTP/1.1” 8.0 之前是 BIO 8.0 之后是NIO
BIO
protocol=“org.apache.coyote.http11.Http11Protocol“
NIO
protocol=”org.apache.coyote.http11.Http11NioProtocol“
AIO
protocol=”org.apache.coyote.http11.Http11Nio2Protocol“
APR
protocol=”org.apache.coyote.http11.Http11AprProtocol“
```

** BIO 线程模型 （Tomcat8之后移除）**

[![](http://files.res.openread.cn/6.png)](http://files.res.openread.cn/6.png)

Acceptor 负责接受连接(封装提交task) 给线程池(去分配work线程)，每个请求都又一个线程去处理

** NIO 线程模型讲解 **

[![](http://files.res.openread.cn/7.png)](http://files.res.openread.cn/7.png)

Acceptor 负责接受连接socket，然后会给Poller使用NIO select ，再 进行分配线程处理

** NIO2 线程模型讲解 **

基于NIO，不再使用多路复用selector，基于事件的异步通知实现，监听各种CompletionHandler，那么为什么有了AIO 还需要NIO？适用场景不同，NIO适合处理较快的场景，不然要一直轮询，如果处理时间长，效率就低下了。AIO 适合时间长的，例如相册服务器，长时间的读取，异步通知效率高

** 源码实现 **
- 1.Http11Protocol  Http BIO协议解析器
	- JIoEndpoint
		- Acceptor implements Runnable
		- SocketProcessor implements Runnable
- 2.Http11NioProtocol Http Nio协议解析器
 	- NioEndpoint
		- Acceptor  implements Runnable
		- Poller implements Runnable 
		- SocketProcessor implements Runnable 
		
** 调优 **
```
Connector
连接器：用于接收 指定协议下的连接 并指定给唯一的Engine 进行处理。
主要属性：
protocol 监听的协议，默认是http/1.1
port 指定服务器端要创建的端口号
minThread	服务器启动时创建的处理请求的线程数
maxThread	最大可以创建的处理请求的线程数
enableLookups	如果为true，则可以通过调用request.getRemoteHost()进行DNS查询来得到远程客户端的实际主机名，若为false则不进行DNS查询，而是返回其ip地址
redirectPort	指定服务器正在处理http请求时收到了一个SSL传输请求后重定向的端口号
acceptCount	指定当所有可以使用的处理请求的线程数都被使用时，可以放到处理队列中的请求数，超过这个数的请求将不予处理。内部有队列
connectionTimeout	指定超时的时间数(以毫秒为单位)
SSLEnabled 是否开启 sll 验证，在Https 访问时需要开启。
```

调休的关键点：为什么这么调？不要凭着感觉调优

https://gitee.com/zhangxiangfeng/apache-tomcat-8-5-14-prod

- 使用线程池,优化策略
- 采用多个Acceptor 加速接受连接，处理加速 合理设置acceptCount  acceptorThreadCount
- JVM设置合理的堆内存

### 3. Tomcat 是如何启动的？

[![](http://files.res.openread.cn/8.png)](http://files.res.openread.cn/8.png)

1.从这里看到 执行 startup.sh == catalina.sh start

[![](http://files.res.openread.cn/9.png)](http://files.res.openread.cn/9.png)

2.从catalina.sh 可以看出 org.apache.catalina.startup.Bootstrap "$@" start

[![](http://files.res.openread.cn/10.png)](http://files.res.openread.cn/10.png)

3.到这里就明白了简单来说就是 Bootstrap.start()启动,也彻底证明了Tomcat是纯Java实现的应用容器

** 源代码 **

```
> org.apache.catalina.startup.Bootstrap.start()  启动入口函数
  > org.apache.catalina.startup.Bootstrap.init() 1.使用类加载器工厂创建不同的类加载器 2.构造 Catalina,反射调用其内部的start的方法
    > org.apache.catalina.startup.Catalina.start 1.调用start(启动Acceptor(接受socket),SocketProcessor(处理socket，编解码，然后给线程给run，出来HttpReq，HttpResp))
      > org.apache.catalina.startup.Catalina.load()
        > org.apache.catalina.startup.Catalina.initDirs() 初始化临时目录
        > org.apache.catalina.startup.Catalina.initNaming() 初始化命名
        > org.apache.catalina.startup.Catalina.createStartDigester() 构造主要的启动者,设置了用来解析server.xml的参数
        > org.apache.catalina.startup.Catalina.configFile() 根据约定规则去默认的base_home目录conf/server.xml去获取server.xml文件，下面使用org.xml.sax.InputSource解析该xml
        > org.apache.tomcat.util.digester.Digester.parse(org.xml.sax.InputSource) 解析上一步的server文件,实例化Server,最终反射调用了org.apache.tomcat.util.IntrospectionUtils.callMethod1 调用setServer设置Server对象
          > org.apache.catalina.startup.SetAllPropertiesRule.begin 设置HttpReq，HttpResp，用于之后的处理
        > org.apache.catalina.Lifecycle.init() 初始化Server
      > org.apache.catalina.Lifecycle.start() 启动Server
        > org.apache.catalina.core.StandardServer.startInternal:413 循环启动所有的service(内部的engine+executors+mapperListener+connectors)都是调用其start方法
          > org.apache.catalina.Lifecycle.start 循环启动service
            > org.apache.catalina.util.LifecycleBase.start 循环启动connectors
              > org.apache.coyote.ProtocolHandler.start 启动  ProtocolHandler 协议处理器
                > org.apache.tomcat.util.net.NioEndpoint.startInternal 内部先启动(pollers，其次是启动所有的Acceptor，代码如下)
                  >// Start poller threads
                       pollers = new Poller[getPollerThreadCount()];
                       for (int i=0; i<pollers.length; i++) {
                           pollers[i] = new Poller();
                           Thread pollerThread = new Thread(pollers[i], getName() + "-ClientPoller-"+i);
                           pollerThread.setPriority(threadPriority);
                           pollerThread.setDaemon(true);
                           pollerThread.start();
                       }
                  //startAcceptorThreads();
                  > org.apache.tomcat.util.net.NioEndpoint.Poller#run 开始通过 selector.selectNow || selector.select(selectorTimeout)监听
                    > org.apache.tomcat.util.net.NioEndpoint.Poller#events 循环所有进来的event,异步执行,放入
                      > org.apache.tomcat.util.net.NioEndpoint.PollerEvent#run 通过 socket.getIOChannel().keyFor(socket.getPoller().getSelector()) 处理某一个event
                        > java.nio.channels.SelectionKey.interestOps(int)
                    > org.apache.tomcat.util.net.NioEndpoint.Poller.processKey 处理NIO的selectKey
                      > org.apache.tomcat.util.net.AbstractEndpoint.processSocket 进一步处理
                        > org.apache.tomcat.util.net.SocketProcessorBase.run
                          > org.apache.tomcat.util.net.SocketProcessorBase.doRun 此方法是抽象的,又此类实现例如 NioEndpoint，Nio2Endpoint，AprEndpoint
                            > org.apache.coyote.Processor.process
                              > org.apache.coyote.AbstractProcessorLight.service
                                > org.apache.coyote.http11.Http11Processor.service 在这里开始解析协议
                                  > org.apache.catalina.connector.CoyoteAdapter.postParseRequest 开始实现Request,Response
                                    > org.apache.catalina.core.ApplicationFilterChain.internalDoFilter  Use potentially wrapped request from this point,Filter执行完毕就开始Servlet.service调用
                                      > javax.servlet.Servlet#service 开始我们的应用
                  > org.apache.tomcat.util.net.AbstractEndpoint.startAcceptorThreads 启动所有的Acceptor(请求入口接收者)
      > java.lang.Runtime.addShutdownHook 添加钩子函数,如果应用被系统kill掉,这里就会调用stop方法优雅退出
```

源代码调试比较复杂，简单来说

1. 构建Catalina 调用start
2. 解析server.xml
3. 初始化启动Server
4. Server 循环启动所有的service(内部的engine+executors+mapperListener+connectors+pollers+startAcceptorThreads)都是调用其start方法
5. 添加 addShutdownHook 钩子函数,清理资源


### 4. Tomcat 如何打破双亲委派

> 篇幅够长了，这里不介绍什么是双亲委派了

- 如何打破双亲委派 1.`重写loadcalss` 2.`使用线程上下文类加载器`
	- 第一次破坏：JDK1.2引入双亲委派，之前的代码都是自定义类加载器，为了兼容，出现loadclass
	- 第二次破坏: 双亲委派解决了基础向上加载的问题，但是基础的类依赖用户的代码如何处理，例如JNDI管理发现用户的资源，出现了 线程上下文类加载器(Thread Context ClassLoader)
	- 第三次破坏: jrebel 热加载和热部署等的出现,java开头的类用系统加载器，否则依赖自己的加载,树状加载转网加载

- 为什么tomca要打破类加载，tomcat有自己的lib目录，一些类要自己去加载

```
1.启动Tomcat有三个线程异步启动
2.org.apache.catalina.startup.Bootstrap.initClassLoaders 分别设置了上下文类加载器(用于加载约定lib下的jar,打破双亲委派)

    private void initClassLoaders() {
        try {
            commonLoader = createClassLoader("common", null);
            if( commonLoader == null ) {
                // no config file, default to this loader - we might be in a 'single' env.
                commonLoader=this.getClass().getClassLoader();
            }
            catalinaLoader = createClassLoader("server", commonLoader);
            sharedLoader = createClassLoader("shared", commonLoader);
        } catch (Throwable t) {
            handleThrowable(t);
            log.error("Class loader creation threw exception", t);
            System.exit(1);
        }
    }
```

Tomcat 如果使用默认的类加载机制行不行？应用的不同lib如何隔离？ 看这里 https://blog.csdn.net/qq_38182963/article/details/78660779
https://blog.csdn.net/zhangcanyan/article/details/78993959