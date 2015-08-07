package cn.wjf.netty.web;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

public class WebSocketServer {

	public void run(int port) throws Exception {
		
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		
		try {
			
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
			.channel(NioServerSocketChannel.class)
			.childHandler(new ChannelInitializer<SocketChannel>() {

				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline pipeline = ch.pipeline();
					//将请求和应答消息编码或解码为HTTP消息
					pipeline.addLast("http-codec", new HttpServerCodec());
					//将HTTP消息的多个组成部分 聚合成一条完整的HTTP消息
					pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
					//处理大数据流
					pipeline.addLast("http-chunked", new ChunkedWriteHandler());
					pipeline.addLast("handler", new WebSocketServerHandler());
				}
				
			});
			
			Channel ch = b.bind(port).sync().channel();
			System.out.println("Web socket server started at port: " + port);
			ch.closeFuture().sync();
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		
		int port = 8800;
		
		if(args != null && args.length > 0) {
			try {
				port = Integer.valueOf(args[0]);
			} catch(NumberFormatException e) {
				//使用默认值
			}
		}
		new WebSocketServer().run(port);
	}
	
}
