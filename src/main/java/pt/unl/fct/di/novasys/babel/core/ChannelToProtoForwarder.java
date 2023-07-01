package pt.unl.fct.di.novasys.babel.core;

import pt.unl.fct.di.novasys.babel.internal.*;
import pt.unl.fct.di.novasys.channel.ChannelEvent;
import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// 这个类从通道中读取对象，并转发
public class ChannelToProtoForwarder implements ChannelListener<BabelMessage> {

    private static final Logger logger = LogManager.getLogger(ChannelToProtoForwarder.class);

    // 是通道的代表
    final int channelId;
    // consumers是 协议的id 和 对应协议的实例
    final Map<Short, GenericProtocol> consumers;

    public ChannelToProtoForwarder(int channelId) {
        this.channelId = channelId;
        consumers = new ConcurrentHashMap<>();
    }

    
    public void addConsumer(short protoId, GenericProtocol consumer) {
        if (consumers.putIfAbsent(protoId, consumer) != null)
            throw new AssertionError("Consumer with protoId " + protoId + " already exists in channel");
    }

    
    
    
    // 这里是真正地从通道拿数据
    @Override
    public void deliverMessage(BabelMessage message, Host host) {
        GenericProtocol channelConsumer;
        //如果目的协议的-1，且消费者有一个
        if (message.getDestProto() == -1 && consumers.size() == 1)
            channelConsumer = consumers.values().iterator().next();
        else
            channelConsumer = consumers.get(message.getDestProto());

        if (channelConsumer == null) {
            logger.error("Channel " + channelId + " received message to protoId " +
                    message.getDestProto() + " which is not registered in channel");
            throw new AssertionError("Channel " + channelId + " received message to protoId " +
                    message.getDestProto() + " which is not registered in channel");
        }
        channelConsumer.deliverMessageIn(new MessageInEvent(message, host, channelId));
    }
    
    //
    @Override
    public void messageSent(BabelMessage addressedMessage, Host host) {
        consumers.values().forEach(c -> c.deliverMessageSent(new MessageSentEvent(addressedMessage, host, channelId)));
    }
    
    //当message发送失败
    @Override
    public void messageFailed(BabelMessage addressedMessage, Host host, Throwable throwable) {
        consumers.values().forEach(c ->
                c.deliverMessageFailed(new MessageFailedEvent(addressedMessage, host, throwable, channelId)));
    }
    
    //传递事件过来
    @Override
    public void deliverEvent(ChannelEvent channelEvent) {
        consumers.values().forEach(v -> v.deliverChannelEvent(new CustomChannelEvent(channelEvent, channelId)));
    }
}
