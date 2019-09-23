package ai.turbochain.ipex.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import ai.turbochain.ipex.component.PrincipalHandshakeHandler;
import ai.turbochain.ipex.component.WebSocketDecoratorFactory;

/*
 * 通过@EnableWebSocketMessageBroker注解开启试用STOMP协议来传输基于代理（message broker）的消息，这时控制器支持使用@MessageMapping
 * 就像使用@RequestMapping一样
 * 
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {
	 @Autowired
	 private WebSocketDecoratorFactory webSocketDecoratorFactory;
	 @Autowired
	 private PrincipalHandshakeHandler principalHandshakeHandler;
	
	/** 
     * 配置了一个简单的消息代理，如果不重载，默认情况下回自动配置一个简单的内存消息代理，用来处理以"/topic"为前缀的消息。
     * 这里重载configureMessageBroker()方法， 
     * 消息代理将会处理前缀为"/topic"的消息。 
     */  
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {//配置消息代理（Message Broker）
    	 /**
         * queue 点对点
         * topic 广播
         * user 点对点前缀
         */
    	config.enableSimpleBroker("/topic");//广播式应配置一个/topic消息代理
        config.setApplicationDestinationPrefixes("/app");
        /* PS
    	 * registry.enableSimpleBroker("/topic", "/user");这句话表示在topic和user这两个域上可以向客户端发消息。
    	 * registry.setUserDestinationPrefix("/user");这句话表示给指定用户发送一对一的主题前缀是"/user"。
    	 * registry.setApplicationDestinationPrefixes("/app");这句话表示客户单向服务器端发送时的主题上面需要加"/app"作为前缀。
    	 */
    }

    
    /** 
     * 将"/market-ws"路径注册为STOMP端点，这个路径与发送和接收消息的目的路径有所不同
     * 这是一个端点，客户端在订阅或发布消息到目的地址前，要连接该端点， 
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
    	//注册STOMP协议的节点(endpoint),并映射的指定的URL
    	registry.addEndpoint("/market-ws").setAllowedOrigins("*")
        .setHandshakeHandler(principalHandshakeHandler)
        .withSockJS()//注册一个STOMP的endpoint,并指定使用SockJS协议
        .setClientLibraryUrl( "https://cdn.jsdelivr.net/sockjs/1.1.4/sockjs.min.js"); 
    	//registry.addEndpoint("/hello").setAllowedOrigins("*").withSokJS();
		//这个和客户端创建连接时的url有关，其中setAllowedOrigins()方法表示允许连接的域名，withSockJS()方法表示支持以SockJS方式连接服务器。
    
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(webSocketDecoratorFactory);
        super.configureWebSocketTransport(registration);
    }
}