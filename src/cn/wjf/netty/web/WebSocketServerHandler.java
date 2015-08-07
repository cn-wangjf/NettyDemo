package cn.wjf.netty.web;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

	private static final Logger logger = Logger.getLogger(WebSocketServerHandler.class.getName());
	
	private WebSocketServerHandshaker handshaker;
	
	@Override
	protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
		if(msg instanceof FullHttpRequest) {	//Http
			handleHttpRequest(ctx, (FullHttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {		//WebSocket
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}
	
	private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
		//如果HTTP解码失败，返回HTTP异常
		if(!req.decoderResult().isSuccess()) {
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
			return;
		}
		//通过http协议发起 WebSocket握手请求
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory("ws://localhost:8800/websocket", null, false);
		handshaker = wsFactory.newHandshaker(req);
		if(handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
		} else {
			handshaker.handshake(ctx.channel(), req);
		}
	}


	private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
		
		//判断是否是关闭链路指令
		if(frame instanceof CloseWebSocketFrame) {
			handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
			return;
		}
		
		//判读是否是ping消息
		if(frame instanceof PingWebSocketFrame) {
			ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
			return;
		}
		
		//仅处理文本消息
		if(!(frame instanceof TextWebSocketFrame)) {
			throw new UnsupportedOperationException(String.format("%s frame type s not supported", frame.getClass().getName()));
		}
		
		//返回应答消息
		String request = ((TextWebSocketFrame) frame).text();
		if(logger.isLoggable(Level.FINE)) {
			logger.fine(String.format("%s received %s", ctx.channel(), request));
		}
		ctx.channel().write(new TextWebSocketFrame(request + ", Now: " + new Date().toString()));
	}

	private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req,FullHttpResponse resp) {
		
		//返回应答给客户端
		if(resp.status().code() != HttpResponseStatus.OK.code()) {
			ByteBuf buf = Unpooled.copiedBuffer(resp.status().toString(), CharsetUtil.UTF_8);
			resp.content().writeBytes(buf);
			buf.release();
			HttpHeaderUtil.setContentLength(resp, resp.content().readableBytes());
		}
		
		//如果是非 Keep-Alive, 关闭连接
		ChannelFuture f = ctx.channel().writeAndFlush(resp);
		if(!HttpHeaderUtil.isKeepAlive(req) || resp.status().code() != HttpResponseStatus.OK.code()) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}


}
