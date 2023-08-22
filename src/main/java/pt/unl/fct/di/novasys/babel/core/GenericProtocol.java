package pt.unl.fct.di.novasys.babel.core;

import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.NoSuchProtocolException;
import pt.unl.fct.di.novasys.babel.handlers.*;
import pt.unl.fct.di.novasys.babel.internal.*;
import pt.unl.fct.di.novasys.babel.metrics.Metric;
import pt.unl.fct.di.novasys.babel.metrics.MetricsManager;
import pt.unl.fct.di.novasys.babel.generic.*;
import pt.unl.fct.di.novasys.channel.ChannelEvent;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An abstract class that represent a generic protocol
 * <p>
 * This class handles all interactions required by protocols
 * <p>
 * Users should extend this class to implement their protocols
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public abstract class GenericProtocol {

    private static final Logger logger = LogManager.getLogger(GenericProtocol.class);

    //TODO split in GenericConnectionlessProtocol and GenericConnectionProtocol?

    private final BlockingQueue<InternalEvent> queue;


    private final BlockingQueue<InternalEvent> ipcqueue;
    //private final BlockingQueue<InternalEvent> orderQueue;
    //private final BlockingQueue<InternalEvent> parallelQueue;
    
    
    //private final BlockingQueue<InternalEvent>  childQueue1;
    //private final BlockingQueue<InternalEvent>  childQueue2;
    //private final BlockingQueue<InternalEvent>  childQueue3;
    //private final BlockingQueue<InternalEvent>  childQueue4;


    //thread id 在用于分发消息的并行处理中使用那个线程
    //public   int  threadID=-1;
    
    // TODO: 2023/6/22 可以考虑线程池的使用 
    
    private final Thread executionThread;
    private final Thread ipcThread;
    //private final Thread  orderExecutionThread;
    //private final Thread  parallelexecutionThread;
  
    
    //private final  Thread  childThread1;
    //private final  Thread  childThread2;
    //private final  Thread  childThread3;
    //private final  Thread  childThread4;
    
    
    // protocol的名字和id
    private final String protoName;
    private final short protoId;
    
    
    // 默认的channel id，在程序的调用方不想多写channel id，默认使用者个
    private int defaultChannel;

    
    //这个字段代替了对通道的事件的处理方法
    private final Map<Integer, ChannelHandlers> channels;
    private final Map<Short, TimerHandler<? extends ProtoTimer>> timerHandlers;
    private final Map<Short, RequestHandler<? extends ProtoRequest>> requestHandlers;
    private final Map<Short, ReplyHandler<? extends ProtoReply>> replyHandlers;
    private final Map<Short, NotificationHandler<? extends ProtoNotification>> notificationHandlers;

    
    // 得到程序中的单例
    private static final Babel babel = Babel.getInstance();

    
    //Debug
    ProtocolMetrics metrics = new ProtocolMetrics();
    //protected ThreadMXBean tmx = ManagementFactory.getThreadMXBean();

    
    
    
    /**
     * Creates a generic protocol with the provided name and numeric identifier
     * and the given event queue policy.
     * <p>
     * Event queue policies can be defined to specify handling events in desired orders:
     * Eg. If multiple events are inside the queue, then timers are always processes first
     * than any other event in the queue.
     *
     * @param protoName the protocol name
     * @param protoId   the protocol numeric identifier
     * @param policy    the queue policy to use
     */
    public GenericProtocol(String protoName, short protoId, BlockingQueue<InternalEvent> policy) {
        this.queue = policy;
        //this.orderQueue=new  LinkedBlockingQueue<>();
        //this.parallelQueue=new LinkedBlockingQueue<>();
        
        //this.childQueue1=new  LinkedBlockingQueue<>();
        //this.childQueue2=new  LinkedBlockingQueue<>();
        //this.childQueue3=new  LinkedBlockingQueue<>();
        //this.childQueue4=new  LinkedBlockingQueue<>();
        
        this.ipcqueue=new LinkedBlockingQueue<>();
        
        this.protoId = protoId;
        this.protoName = protoName;

        //TODO change to event loop (simplifies the deliver->poll->handle process)
        //TODO only change if performance better
        this.executionThread = new Thread(this::mainLoop, protoId + "-" + protoName+"mainLoop");
        this.ipcThread=new Thread(this::ipcLoop, protoId + "-" + protoName+"ipcLoop");
        //this.parallelexecutionThread=new  Thread(this::partiLoop, protoId + "-" + protoName+"-parallel");
        //this.orderExecutionThread=new Thread(this::orderLoop,protoId + "-" + protoName+"-Order");  
        
        //this.childThread1= new Thread(this::childLoop1, protoId + "-" + protoName+"-childThread1");
        //this.childThread2= new Thread(this::childLoop2, protoId + "-" + protoName+"-childThread2");
        //this.childThread3= new Thread(this::childLoop3, protoId + "-" + protoName+"-childThread3");
        //this.childThread4= new Thread(this::childLoop4, protoId + "-" + protoName+"-childThread4");
        //
        
        channels = new HashMap<>();
        defaultChannel = -1;

        //Initialize maps for event handlers
        this.timerHandlers = new HashMap<>();
        this.requestHandlers = new HashMap<>();
        this.replyHandlers = new HashMap<>();
        this.notificationHandlers = new HashMap<>();

        //tmx.setThreadContentionMonitoringEnabled(true);
    }

    /**
     * Create a generic protocol with the provided name and numeric identifier
     * and network service
     * <p>
     * The internal event queue is defined to have a FIFO policy on all events
     *
     * @param protoName name of the protocol
     * @param protoId   numeric identifier
     */
    public GenericProtocol(String protoName, short protoId) {
        this(protoName, protoId, new LinkedBlockingQueue<>());
    }

    
    /**
     * Returns the numeric identifier of the protocol
     *
     * @return numeric identifier
     */
    public final short getProtoId() {
        return protoId;
    }

    /**
     * Returns the name of the protocol
     *
     * @return name
     */
    public final String getProtoName() {
        return protoName;
    }

    
    
    
    
    /**
     * Initializes the protocol with the given properties
     *
     * @param props properties
     */
    public abstract void init(Properties props) throws HandlerRegistrationException, IOException;

    
    
    /**
     * Start the execution thread of the protocol
     */
    public final void start() {
        this.executionThread.start();
        this.ipcThread.start();
        //this.orderExecutionThread.start();
        //this.parallelexecutionThread.start();
        
        //this.childThread1.start();
        //this.childThread2.start();
        //this.childThread3.start();
        //this.childThread4.start();
    }
    
    

    public ProtocolMetrics getMetrics() {
        return metrics;
    }

    protected long getMillisSinceBabelStart(){
        return babel.getMillisSinceStart();
    }

    
    
    
    
    
    
    
    //------------------ PROTOCOL REGISTERS -------------------------------------

    protected void registerMetric(Metric m){
        MetricsManager.getInstance().registerMetric(m);
    }

    
    
    // 不管是messager，还是ipc还是 subscribe  timer 都使用这个注册处理函数
    private <V> void registerHandler(short id, V handler, Map<Short, V> handlerMap)
            throws HandlerRegistrationException {
        if (handlerMap.putIfAbsent(id, handler) != null) {
            throw new HandlerRegistrationException("Conflict in registering handler for "
                    + handler.getClass().toString() + " with id " + id + ".");
        }
    }

    
    
    
    
    
    /**
     * Register a message inHandler for the protocol to process message events
     * form the network
     *
     * @param cId       the id of the channel
     * @param msgId     the numeric identifier of the message event
     * @param inHandler the function to process message event
     * @throws HandlerRegistrationException if a inHandler for the message id is already registered
     */
    protected final <V extends ProtoMessage> void registerMessageHandler(int cId, short msgId,
                                                                         MessageInHandler<V> inHandler)
            throws HandlerRegistrationException {
        registerMessageHandler(cId, msgId, inHandler, null, null);
    }

    /**
     * Register a message inHandler for the protocol to process message events
     * form the network
     *
     * @param cId         the id of the channel
     * @param msgId       the numeric identifier of the message event
     * @param inHandler   the function to handle a received message event
     * @param sentHandler the function to handle a sent message event
     * @throws HandlerRegistrationException if a inHandler for the message id is already registered
     */
    protected final <V extends ProtoMessage> void registerMessageHandler(int cId, short msgId,
                                                                         MessageInHandler<V> inHandler,
                                                                         MessageSentHandler<V> sentHandler)
            throws HandlerRegistrationException {
        registerMessageHandler(cId, msgId, inHandler, sentHandler, null);
    }

    /**
     * Register a message inHandler for the protocol to process message events
     * form the network
     *
     * @param cId         the id of the channel
     * @param msgId       the numeric identifier of the message event
     * @param inHandler   the function to handle a received message event
     * @param failHandler the function to handle a failed message event
     * @throws HandlerRegistrationException if a inHandler for the message id is already registered
     */

    protected final <V extends ProtoMessage> void registerMessageHandler(int cId, short msgId,
                                                                         MessageInHandler<V> inHandler,
                                                                         MessageFailedHandler<V> failHandler)
            throws HandlerRegistrationException {
        registerMessageHandler(cId, msgId, inHandler, null, failHandler);
    }
    // 注册消息处理器的实际执行者
    /**
     * Register a message inHandler for the protocol to process message events
     * form the network
     *
     * @param cId         the id of the channel
     * @param msgId       the numeric identifier of the message event
     * @param inHandler   the function to handle a received message event
     * @param sentHandler the function to handle a sent message event
     * @param failHandler the function to handle a failed message event
     * @throws HandlerRegistrationException if a inHandler for the message id is already registered
     */
    protected final <V extends ProtoMessage> void registerMessageHandler(int cId, short msgId,
                                                                         MessageInHandler<V> inHandler,
                                                                         MessageSentHandler<V> sentHandler,
                                                                         MessageFailedHandler<V> failHandler)
            throws HandlerRegistrationException {
        registerHandler(msgId, inHandler, getChannelOrThrow(cId).messageInHandlers);
        if (sentHandler != null) registerHandler(msgId, sentHandler, getChannelOrThrow(cId).messageSentHandlers);
        if (failHandler != null) registerHandler(msgId, failHandler, getChannelOrThrow(cId).messageFailedHandlers);
    }

  
    
    
    // ------------------注册event的处理函数
    
    /**
     * Register an handler to process a channel-specific event
     *
     * @param cId     the id of the channel
     * @param eventId the id of the event to process
     * @param handler the function to handle the event
     * @throws HandlerRegistrationException if a inHandler for the event id is already registered
     */
    protected final <V extends ChannelEvent> void registerChannelEventHandler(int cId, short eventId,
                                                                              ChannelEventHandler<V> handler)
            throws HandlerRegistrationException {
        registerHandler(eventId, handler, getChannelOrThrow(cId).channelEventHandlers);
    }

    
    
    
    //--------注册时钟
    
    /**
     * Register a timer handler for the protocol to process timer events
     *
     * @param timerID the numeric identifier of the timer event
     * @param handler the function to process timer event
     * @throws HandlerRegistrationException if a handler for the timer timerID is already registered
     */
    protected final <V extends ProtoTimer> void registerTimerHandler(short timerID,
                                                                     TimerHandler<V> handler)
            throws HandlerRegistrationException {
        registerHandler(timerID, handler, timerHandlers);
    }

    
    
    
    
    //----------------注册ipc请求
    
    /**
     * Register a request handler for the protocol to process request events
     *
     * @param requestId the numeric identifier of the request event
     * @param handler   the function to process request event
     * @throws HandlerRegistrationException if a handler for the request requestId is already registered
     */
    protected final <V extends ProtoRequest> void registerRequestHandler(short requestId,
                                                                         RequestHandler<V> handler)
            throws HandlerRegistrationException {
        registerHandler(requestId, handler, requestHandlers);
    }

    /**
     * Register a reply handler for the protocol to process reply events
     *
     * @param replyId the numeric identifier of the reply event
     * @param handler the function to process reply event
     * @throws HandlerRegistrationException if a handler for the reply replyId is already registered
     */
    protected final <V extends ProtoReply> void registerReplyHandler(short replyId, ReplyHandler<V> handler)
            throws HandlerRegistrationException {
        registerHandler(replyId, handler, replyHandlers);
    }

    
    
    
    
    
    
    // ------------------------- NETWORK/CHANNELS ----------------------
    
    //得到对应chanel的channel事件处理实例；也有判断处理实例是否存在的作用——不存在直接爆出异常
    private ChannelHandlers getChannelOrThrow(int channelId) {
        ChannelHandlers handlers = channels.get(channelId);
        if (handlers == null)
            throw new AssertionError("Channel does not exist: " + channelId);
        return handlers;
    }
    
    
    
    // 注册一个序列化和反序列化
    /**
     * Registers a (de)serializer for a message type
     *
     * @param msgId      the message id
     * @param serializer the serializer for the given message id
     */
    protected final void registerMessageSerializer(int channelId, short msgId,
                                                   ISerializer<? extends ProtoMessage> serializer) {
        babel.registerSerializer(channelId, msgId, serializer);
    }
    
    
    
    // 每个协议创建一个TCP通道
    /**
     * Creates a new channel
     *
     * @param channelName the name of the channel
     * @param props       channel-specific properties. See the documentation for each channel.
     * @return the id of the newly created channel
     */
    protected final int createChannel(String channelName, Properties props) throws IOException {
        int channelId = babel.createChannel(channelName, this.protoId, props);
        registerSharedChannel(channelId);//使用这个将这个协议设定为这个chanel的消费者
        return channelId;
    }
    
    
    // 这个方法向babel注册了开辟通道的消费协议，一般是谁创建谁，谁消费
    protected final void registerSharedChannel(int channelId) {
        babel.registerChannelInterest(channelId, this.protoId, this);
        channels.put(channelId, new ChannelHandlers());
        if (defaultChannel == -1)
            setDefaultChannel(channelId);
    }
    
    
    // 设置默认的通道:目的是在 open  close  sendMsg 默认通道作为一个缺省的参数
    /**
     * Sets the default channel for the {@link #sendMessage(ProtoMessage, Host)}, {@link #openConnection(Host)}
     * and {@link #closeConnection(Host)} methods.
     *
     * @param channelId the channel id
     */
    protected final void setDefaultChannel(int channelId) {
        getChannelOrThrow(channelId);
        defaultChannel = channelId;
    }

    
    
    
    
    
    
    
    /**
     * Sends a message to a specified destination, using the default channel.
     * May require the use of {@link #openConnection(Host)} beforehand.
     *
     * @param msg         the message to send
     * @param destination the ip/port to send the message to
     */
    protected final void sendMessage(ProtoMessage msg, Host destination) {
        sendMessage(defaultChannel, msg, this.protoId, destination, 0);
    }
    

    /**
     * Sends a message to a specified destination using the given channel.
     * May require the use of {@link #openConnection(Host)} beforehand.
     *
     * @param channelId     the channel to send the message through
     * @param msg         the message to send
     * @param destination the ip/port to send the message to
     */
    protected final void sendMessage(int channelId, ProtoMessage msg, Host destination) {
        sendMessage(channelId, msg, this.protoId, destination, 0);
    }
    
    
    // TODO: 2023/6/23  在发送排序消息时，使用这个方法
    
    /**
     * Sends a message to a different protocol in the specified destination, using the default channel.
     * May require the use of {@link #openConnection(Host)} beforehand.
     *
     * @param destProto   the target protocol for the message.
     * @param msg         the message to send
     * @param destination the ip/port to send the message to
     */
    protected final void sendMessage(ProtoMessage msg, short destProto, Host destination) {
        sendMessage(defaultChannel, msg, destProto, destination, 0);
    }

    /**
     * Sends a message to a specified destination, using the default channel, and a specific connection in that channel.
     * May require the use of {@link #openConnection(Host)} beforehand.
     *
     * @param connection  the channel-specific connection to use.
     * @param msg         the message to send
     * @param destination the ip/port to send the message to
     */
    protected final void sendMessage(ProtoMessage msg, Host destination, int connection) {
        sendMessage(defaultChannel, msg, this.protoId, destination, connection);
    }

    /**
     * Sends a message to a specified destination, using a specific connection in a given channel.
     * May require the use of {@link #openConnection(Host)} beforehand.
     *
     * @param channelId     the channel to send the message through
     * @param connection  the channel-specific connection to use.
     * @param msg         the message to send
     * @param destination the ip/port to send the message to
     */
    protected final void sendMessage(int channelId, ProtoMessage msg, Host destination, int connection) {
        sendMessage(channelId, msg, this.protoId, destination, connection);
    }

    /**
     * Sends a message to a different protocol in the specified destination,
     * using a specific connection in the default channel.
     * May require the use of {@link #openConnection(Host)} beforehand.
     *
     * @param destProto   the target protocol for the message.
     * @param connection  the channel-specific connection to use.
     * @param msg         the message to send
     * @param destination the ip/port to send the message to
     */
    protected final void sendMessage(ProtoMessage msg, short destProto, Host destination, int connection) {
        sendMessage(defaultChannel, msg, destProto, destination, connection);
    }
    
    
    // sendMessage的实际执行者
    /**
     * Sends a message to a different protocol in the specified destination,
     * using a specific connection in the given channel.
     * May require the use of {@link #openConnection(Host)} beforehand.
     *
     * @param channelId   the channel to send the message through
     * @param destProto   the target protocol for the message.
     * @param connection  the channel-specific connection to use.
     * @param msg         the message to send
     * @param destination the ip/port to send the message to
     */
    protected final void sendMessage(int channelId, ProtoMessage msg, short destProto,
                                     Host destination, int connection) {
        getChannelOrThrow(channelId);
        if (logger.isDebugEnabled())
            logger.debug("Sending: " + msg + " to " + destination + " proto " + destProto +
                    " channel " + channelId);
        babel.sendMessage(channelId, connection, new BabelMessage(msg, this.protoId, destProto), destination);
    }

    
    
    
    
    
    
    
    //关于通道的打开和关闭
    /**
     * Open a connection to the given peer using the default channel.
     * Depending on the channel, this method may be unnecessary/forbidden.
     *
     * @param peer the ip/port to create the connection to.
     */
    protected final void openConnection(Host peer) {
        openConnection(peer, defaultChannel);
    }

    //打开通道的实际执行者
    /**
     * Open a connection to the given peer using the given channel.
     * Depending on the channel, this method may be unnecessary/forbidden.
     *
     * @param peer      the ip/port to create the connection to.
     * @param channelId the channel to create the connection in
     */
    protected final void openConnection(Host peer, int channelId) {
        babel.openConnection(channelId, peer);
    }

    
    
    
    /**
     * Closes the connection to the given peer using the default channel.
     * Depending on the channel, this method may be unnecessary/forbidden.
     *
     * @param peer the ip/port to close the connection to.
     */
    protected final void closeConnection(Host peer) {
        closeConnection(peer, defaultChannel);
    }
    
    
    /**
     * Closes the connection to the given peer in the given channel.
     * Depending on the channel, this method may be unnecessary/forbidden.
     *
     * @param peer      the ip/port to close the connection to.
     * @param channelId the channel to close the connection in
     */
    protected final void closeConnection(Host peer, int channelId) {
        closeConnection(peer, channelId, protoId);
    }

    
    /**
     * Closes a specific connection to the given peer in the given channel.
     * Depending on the channel, this method may be unnecessary/forbidden.
     *
     * @param peer       the ip/port to close the connection to.
     * @param channelId  the channel to close the connection in
     * @param connection the channel-specific connection to close
     */
    
    //关闭通道的实际执行者
    protected final void closeConnection(Host peer, int channelId, int connection) {
        //这个connection是没有使用的
        babel.closeConnection(channelId, peer, connection);
    }

    
    
    
    
    
    // ------------------ IPC BABEL PROXY ---------------------

    /**
     * Sends a request to the destination protocol
     *
     * @param request     request event
     * @param destination the destination protocol
     * @throws NoSuchProtocolException if the protocol does not exists
     */
    protected final void sendRequest(ProtoRequest request, short destination) throws NoSuchProtocolException {
        babel.sendIPC(new IPCEvent(request, protoId, destination));
    }

    /**
     * Sends a reply to the destination protocol
     *
     * @param destination the destination protocol
     * @param reply       reply event
     * @throws NoSuchProtocolException if the protocol does not exists
     */
    protected final void sendReply(ProtoReply reply, short destination) throws NoSuchProtocolException {
        babel.sendIPC(new IPCEvent(reply, protoId, destination));
    }

    
    
    
    
    // ------------------------------ NOTIFICATION BABEL PROXY ---------------------------------

    /**
     * Subscribes a notification, executing the given callback everytime it is triggered by any protocol.
     *
     * @param nId the id of the notification to subscribe to
     * @param h   the callback to execute upon receiving the notification
     * @throws HandlerRegistrationException if there is already a callback for the notification
     */
    protected final <V extends ProtoNotification> void subscribeNotification(short nId, NotificationHandler<V> h)
            throws HandlerRegistrationException {
        registerHandler(nId, h, notificationHandlers);
        babel.subscribeNotification(nId, this);
    }

    /**
     * Unsubscribes a notification.
     *
     * @param nId the id of the notification to unsubscribe from
     */
    protected final void unsubscribeNotification(short nId) {
        notificationHandlers.remove(nId);
        babel.unsubscribeNotification(nId, this);
    }
    
    
    
    // 触发一个Notification
    /**
     * Triggers a notification, causing every protocol that subscribe it to execute its callback.
     *
     * @param n the notification event to trigger
     */
    protected final void triggerNotification(ProtoNotification n) {
        babel.triggerNotification(new NotificationEvent(n, protoId));
    }

    
    
    
    
    
    // -------------------------- TIMER BABEL PROXY ----------------------- 

    /**
     * Setups a period timer
     *
     * @param timer  the timer event
     * @param first  timeout until first trigger (in milliseconds)
     * @param period periodicity (in milliseconds)
     * @return unique identifier of the timer set
     */
    protected long setupPeriodicTimer(ProtoTimer timer, long first, long period) {
        return babel.setupPeriodicTimer(timer, this,protoId, first, period);
    }

    /**
     * Setups a timer
     *
     * @param t       the timer event
     * @param timeout timout until trigger (in milliseconds)
     * @return unique identifier of the t set
     */
    protected long setupTimer(ProtoTimer t, long timeout) {
        return babel.setupTimer(t, this,protoId, timeout);
    }

    /**
     * Cancel the timer with the provided unique identifier
     *
     * @param timerID timer unique identifier
     * @return the canceled timer event, or null if it wasn't set or have already been trigger and was not periodic
     */
    protected ProtoTimer cancelTimer(long timerID) {
        return babel.cancelTimer(timerID,protoId);
    }

    
    
    
    
    
    // --------- DELIVERERS FROM BABEL -------实质是 Babel中channelto-------------
    
    /**
     * Used by babel to deliver channel messages to protocols.
     */
    final protected void deliverMessageIn(MessageInEvent msgIn) {
        //if (protoId==300){//
        //    short  msgthreadid=msgIn.getMsg().getMessage().getThreadid();
        //    //在接收到accept和acceptack消息时,进入并行线程开始处理
        //    switch (msgthreadid) {
        //        case 1:
        //            childQueue1.add(msgIn);
        //            return;
        //        case 2:
        //            childQueue2.add(msgIn);
        //            return;
        //        case 3:
        //            childQueue3.add(msgIn);
        //            return;
        //        case 4:
        //            childQueue4.add(msgIn);
        //            return;
        //    }
        //}else{
        //    orderQueue.add(msgIn);
        //}
        queue.add(msgIn);
    }

    /**
     * Used by babel to deliver channel message sent events to protocols. Do not evoke directly.
     */
    final void deliverMessageSent(MessageSentEvent event) {
        //if (protoId==300){//
        //    short  msgthreadid=event.getMsg().getMessage().getThreadid();
        //    //在接收到accept和acceptack消息时,进入并行线程开始处理
        //    switch (msgthreadid) {
        //        case 1:
        //            childQueue1.add(event);
        //            return;
        //        case 2:
        //            childQueue2.add(event);
        //            return;
        //        case 3:
        //            childQueue3.add(event);
        //            return;
        //        case 4:
        //            childQueue4.add(event);
        //            return;
        //    }
        //}else{
        //    orderQueue.add(event);
        //}
        queue.add(event);
    }

    /**
     * Used by babel to deliver channel message failed events to protocols. Do not evoke directly.
     */
    final void deliverMessageFailed(MessageFailedEvent event) {
        //if (protoId==300){//
        //    short  msgthreadid=event.getMsg().getMessage().getThreadid();
        //    //在接收到accept和acceptack消息时,进入并行线程开始处理
        //    switch (msgthreadid) {
        //        case 1:
        //            childQueue1.add(event);
        //            return;
        //        case 2:
        //            childQueue2.add(event);
        //            return;
        //        case 3:
        //            childQueue3.add(event);
        //            return;
        //        case 4:
        //            childQueue4.add(event);
        //            return;
        //    }
        //}else{
        //    orderQueue.add(event);
        //}
        queue.add(event);
    }

    
    // 这是协议之间的组件通信
    
    /**
     * Used by babel to deliver timer events to protocols. Do not evoke directly.
     */
    final void deliverTimer(TimerEvent timer) {
        //if (protoId==300){
        //    switch (threadID) {
        //        case 1:
        //            childQueue1.add(timer);
        //            return;
        //        case 2:
        //            childQueue2.add(timer);
        //            return;
        //        case 3:
        //            childQueue3.add(timer);
        //            return;
        //        case 4:
        //            childQueue4.add(timer);
        //            return;
        //        default:
        //            orderQueue.add(timer);
        //    }
        //}else{
        //    orderQueue.add(timer);
        //}
        queue.add(timer);
    }

    /**
     * Used by babel to deliver notifications to protocols. Do not evoke directly.
     */
    final void deliverNotification(NotificationEvent notification) {
        //if (protoId==300){//
        //    switch (threadID) {
        //        case 1:
        //            childQueue1.add(notification);
        //            return;
        //        case 2:
        //            childQueue2.add(notification);
        //            return;
        //        case 3:
        //            childQueue3.add(notification);
        //            return;
        //        case 4:
        //            childQueue4.add(notification);
        //            return;
        //        default:
        //            orderQueue.add(notification);
        //    }
        //}else{
        //    orderQueue.add(notification);
        //}
        queue.add(notification);
    }

    /**
     * Used by babel to deliver requests/replies to protocols. Do not evoke directly.
     */
    final void deliverIPC(IPCEvent ipc) {
        queue.add(ipc);
        //if (protoId!=100 && protoId!=200){
        //    if(ipc.getIpc().getType()==ProtoIPC.Type.REQUEST){
        //        // 只有这一种情况 ：是Front层发过来的ipc写
        //        ipcqueue.add(ipc);
        //    }else {
        //        queue.add(ipc);
        //    }
        //} else {
        //    queue.add(ipc);
        //}
    }


    /**
     * Used by babel to deliver channel events to protocols. Do not evoke directly.
     */
    final void deliverChannelEvent(CustomChannelEvent event) {
        queue.add(event);
        //if (protoId==300){//
        //    switch (threadID) {
        //        case 1:
        //            childQueue1.add(event);
        //            return;
        //        case 2:
        //            childQueue2.add(event);
        //            return;
        //        case 3:
        //            childQueue3.add(event);
        //            return;
        //        case 4:
        //            childQueue4.add(event);
        //            return;
        //        default:
        //            orderQueue.add(event);
        //    }
        //}else{
        //    orderQueue.add(event);
        //}
    }
    
    
    
    
    
    
    // ------------------ MAIN LOOP -------------------------------------------------
    private void mainLoop() {
        while (true) {
            try {
                InternalEvent pe = this.queue.take();
                metrics.totalEventsCount++;
                if (logger.isDebugEnabled())
                    logger.debug("Handling event: " + pe);
                switch (pe.getType()) {
                    case MESSAGE_IN_EVENT:
                        metrics.messagesInCount++;
                        //if (protoId==300){
                        //    MessageInEvent msgIn=(MessageInEvent)pe;
                        //    short  msgthreadid=msgIn.getMsg().getMessage().getThreadid();
                        //    //在接收到accept和acceptack消息时,进入并行线程开始处理
                        //    switch (msgthreadid) {
                        //        case 1:
                        //            childQueue1.add(msgIn);
                        //            break;
                        //        case 2:
                        //            childQueue2.add(msgIn);
                        //            break;
                        //        case 3:
                        //            childQueue3.add(msgIn);
                        //            break;
                        //        case 4:
                        //            childQueue4.add(msgIn);
                        //            break;
                        //        default:
                        //            
                        //    }
                        //}else{
                        //    this.handleMessageIn((MessageInEvent) pe);
                        //}
                        this.handleMessageIn((MessageInEvent) pe);
                        break;
                    case MESSAGE_FAILED_EVENT:
                        metrics.messagesFailedCount++;
                        //if (protoId==300){
                        //    MessageFailedEvent msgFail=(MessageFailedEvent)pe;
                        //    short  msgthreadid=msgFail.getMsg().getMessage().getThreadid();
                        //    switch (msgthreadid) {
                        //        case 1:
                        //            childQueue1.add(msgFail);
                        //            break;
                        //        case 2:
                        //            childQueue2.add(msgFail);
                        //            break;
                        //        case 3:
                        //            childQueue3.add(msgFail);
                        //            break;
                        //        case 4:
                        //            childQueue4.add(msgFail);
                        //            break;
                        //        default:
                        //            logger.warn("不应该到这在消息分发中");
                        //    }
                        //}else{
                        //    this.handleMessageFailed((MessageFailedEvent) pe);
                        //}
                        this.handleMessageFailed((MessageFailedEvent) pe);
                        break;
                    case MESSAGE_SENT_EVENT:
                        metrics.messagesSentCount++;
                        //if (protoId==300){
                        //    MessageSentEvent msgSent=(MessageSentEvent)pe;
                        //    short  msgthreadid=msgSent.getMsg().getMessage().getThreadid();
                        //    switch (msgthreadid) {
                        //        case 1:
                        //            childQueue1.add(msgSent);
                        //            break;
                        //        case 2:
                        //            childQueue2.add(msgSent);
                        //            break;
                        //        case 3:
                        //            childQueue3.add(msgSent);
                        //            break;
                        //        case 4:
                        //            childQueue4.add(msgSent);
                        //            break;
                        //        default:
                        //            logger.warn("不应该到这在消息分发中");
                        //    }
                        //}else{
                        //    this.handleMessageSent((MessageSentEvent) pe);
                        //}
                        this.handleMessageSent((MessageSentEvent) pe);
                        break;
                    case TIMER_EVENT:
                        metrics.timersCount++;
                        this.handleTimer((TimerEvent) pe);
                        break;
                    case NOTIFICATION_EVENT:
                        metrics.notificationsCount++;
                        this.handleNotification((NotificationEvent) pe);
                        break;
                    case IPC_EVENT:
                        IPCEvent i = (IPCEvent) pe;
                        switch (i.getIpc().getType()) {
                            case REPLY:
                                metrics.repliesCount++;
                                handleReply((ProtoReply) i.getIpc(), i.getSenderID());
                                break;
                            case REQUEST:
                                metrics.requestsCount++;
                                handleRequest((ProtoRequest) i.getIpc(), i.getSenderID());
                                break;
                            default:
                                throw new AssertionError("Ups");
                        }
                        break;
                    case CUSTOM_CHANNEL_EVENT:
                        metrics.customChannelEventsCount++;
                        this.handleChannelEvent((CustomChannelEvent) pe);
                        break;
                    default:
                        throw new AssertionError("Unexpected event received by babel. protocol "
                                + protoId + " (" + protoName + "-mainLoop)");
                }
            } catch (Exception e) {
                logger.error("Unhandled exception in protocol " + getProtoName() +" ("+ getProtoId() +"-mainLoop) " + e, e);
                e.printStackTrace();
            }
        }
    }
    
    //private void orderLoop() {
    //    while (true) {
    //        try {
    //            InternalEvent pe = this.orderQueue.take();
    //            if (logger.isDebugEnabled())
    //                logger.debug("Handling event: " + pe);
    //            switch (pe.getType()) {
    //                case MESSAGE_IN_EVENT:
    //                    this.handleMessageIn((MessageInEvent) pe);
    //                    break;
    //                case MESSAGE_FAILED_EVENT:
    //                    this.handleMessageFailed((MessageFailedEvent) pe);
    //                    break;
    //                case MESSAGE_SENT_EVENT:
    //                    this.handleMessageSent((MessageSentEvent) pe);
    //                    break;
    //                case TIMER_EVENT:
    //                    this.handleTimer((TimerEvent) pe);
    //                    break;
    //                case NOTIFICATION_EVENT:
    //                    this.handleNotification((NotificationEvent) pe);
    //                    break;
    //                case IPC_EVENT:
    //                    IPCEvent i = (IPCEvent) pe;
    //                    switch (i.getIpc().getType()) {
    //                        case REPLY:
    //                            handleReply((ProtoReply) i.getIpc(), i.getSenderID());
    //                            break;
    //                        case REQUEST:
    //                            handleRequest((ProtoRequest) i.getIpc(), i.getSenderID());
    //                            break;
    //                        default:
    //                            throw new AssertionError("Ups");
    //                    }
    //                    break;
    //                case CUSTOM_CHANNEL_EVENT:
    //                    this.handleChannelEvent((CustomChannelEvent) pe);
    //                    break;
    //                default:
    //                    throw new AssertionError("Unexpected event received by babel. protocol "
    //                            + protoId + " (" + protoName + "-orderLoop)");
    //            }
    //        } catch (Exception e) {
    //            logger.error("Unhandled exception in protocol " + getProtoName() +" ("+ getProtoId() +"-orderLoop) " + e, e);
    //            e.printStackTrace();
    //        }
    //    }
    //}
    //
    //// 只有TPOChain的算法需要这个底层依赖,其他算法还是原来的依赖,这点要注意
    //private void  partiLoop(){
    //    while (true) {
    //        try {
    //            InternalEvent pe = this.parallelQueue.take();
    //            switch (pe.getType()) {
    //                case MESSAGE_IN_EVENT:
    //                    MessageInEvent inm=(MessageInEvent) pe;
    //                    // 能进入这个通道的mssage都是201 202
    //                    short threadid=inm.getMsg().getMessage().getThreadid();
    //                    switch (threadid) {
    //                        case 1:
    //                            childQueue1.add(pe);
    //                            break;
    //                        case 2:
    //                            childQueue2.add(pe);
    //                            break;
    //                        case 3:
    //                            childQueue3.add(pe);
    //                            break;
    //                        case 4:
    //                            childQueue4.add(pe);
    //                            break;
    //                        default:
    //                            orderQueue.add(pe);
    //                            break;
    //                    }
    //                    break;
    //                case MESSAGE_FAILED_EVENT:
    //                    MessageFailedEvent failm=(MessageFailedEvent) pe;
    //                    // 能进入这个通道的mssage都是201 202
    //                    short failthreadid=failm.getMsg().getMessage().getThreadid();
    //                    switch (failthreadid) {
    //                        case 1:
    //                            childQueue1.add(pe);
    //                            break;
    //                        case 2:
    //                            childQueue2.add(pe);
    //                            break;
    //                        case 3:
    //                            childQueue3.add(pe);
    //                            break;
    //                        case 4:
    //                            childQueue4.add(pe);
    //                            break;
    //                        default:
    //                            orderQueue.add(pe);
    //                            break;
    //                    }
    //                    //this.handleMessageFailed((MessageFailedEvent) pe);
    //                    break;
    //                case MESSAGE_SENT_EVENT:
    //                    MessageSentEvent sendm=(MessageSentEvent) pe;
    //                    // 能进入这个通道的mssage都是201 202
    //                    short sendthreadid=sendm.getMsg().getMessage().getThreadid();
    //                    switch (sendthreadid) {
    //                        case 1:
    //                            childQueue1.add(pe);
    //                            break;
    //                        case 2:
    //                            childQueue2.add(pe);
    //                            break;
    //                        case 3:
    //                            childQueue3.add(pe);
    //                            break;
    //                        case 4:
    //                            childQueue4.add(pe);
    //                            break;
    //                        default:
    //                            orderQueue.add(pe);
    //                            break;
    //                    }
    //                    //this.handleMessageSent((MessageSentEvent) pe);
    //                    break;
    //                case TIMER_EVENT:
    //                    switch (threadID) {
    //                        case 1:
    //                            childQueue1.add(pe);
    //                            break;
    //                        case 2:
    //                            childQueue2.add(pe);
    //                            break;
    //                        case 3:
    //                            childQueue3.add(pe);
    //                            break;
    //                        case 4:
    //                            childQueue4.add(pe);
    //                            break;
    //                        default:
    //                            orderQueue.add(pe);
    //                            break;
    //                    }
    //                    break;
    //                case NOTIFICATION_EVENT:
    //                    //this.handleNotification((NotificationEvent) pe);
    //                    break;
    //                case IPC_EVENT:
    //                    IPCEvent i = (IPCEvent) pe;
    //                    switch (i.getIpc().getType()) {
    //                        case REPLY:
    //                            handleReply((ProtoReply) i.getIpc(), i.getSenderID());
    //                            break;
    //                        case REQUEST:
    //                            switch (threadID) {
    //                                case 1:
    //                                    childQueue1.add(pe);
    //                                    break;
    //                                case 2:
    //                                    childQueue2.add(pe);
    //                                    break;
    //                                case 3:
    //                                    childQueue3.add(pe);
    //                                    break;
    //                                case 4:
    //                                    childQueue4.add(pe);
    //                                    break;
    //                                default:
    //                                    orderQueue.add(pe);
    //                                    break;
    //                            }
    //                            //handleRequest((ProtoRequest) i.getIpc(), i.getSenderID());
    //                            break;
    //                        default:
    //                            throw new AssertionError("Ups");
    //                    }
    //                    break;
    //                case CUSTOM_CHANNEL_EVENT:
    //                    //this.handleChannelEvent((CustomChannelEvent) pe);
    //                    break;
    //                default:
    //                    throw new AssertionError("Unexpected event received by babel. protocol "
    //                            + protoId + " (" + protoName + "-partiLoop)");
    //            }
    //        } catch (Exception e) {
    //            logger.error("Unhandled exception in protocol " + getProtoName() +" ("+ getProtoId() +"-partiLoop) " + e, e);
    //            e.printStackTrace();
    //        }
    //    }
    //}
    
    
    
    // 四个子线程的Runnnable
    //private  void   childLoop1(){
    //    while (true) {
    //        try {
    //            InternalEvent pe = this.childQueue1.take();
    //            switch (pe.getType()) {
    //                case MESSAGE_IN_EVENT:
    //                    this.handleMessageIn((MessageInEvent) pe);
    //                    break;
    //                case MESSAGE_FAILED_EVENT:
    //                    this.handleMessageFailed((MessageFailedEvent) pe);
    //                    break;
    //                case MESSAGE_SENT_EVENT:
    //                    this.handleMessageSent((MessageSentEvent) pe);
    //                    break;
    //                case TIMER_EVENT:
    //                    this.handleTimer((TimerEvent) pe);
    //                    break;
    //                case NOTIFICATION_EVENT:
    //                    this.handleNotification((NotificationEvent) pe);
    //                    break;
    //                case IPC_EVENT:
    //                    IPCEvent i = (IPCEvent) pe;
    //                    switch (i.getIpc().getType()) {
    //                        case REPLY:
    //                            handleReply((ProtoReply) i.getIpc(), i.getSenderID());
    //                            break;
    //                        case REQUEST:
    //                            handleRequest((ProtoRequest) i.getIpc(), i.getSenderID());
    //                            break;
    //                        default:
    //                            throw new AssertionError("Ups");
    //                    }
    //                    break;
    //                case CUSTOM_CHANNEL_EVENT:
    //                    this.handleChannelEvent((CustomChannelEvent) pe);
    //                    break;
    //                default:
    //                    throw new AssertionError("Unexpected event received by babel. protocol "
    //                            + protoId + " (" + protoName + "-childLoop1)");
    //            }
    //        } catch (Exception e) {
    //            logger.error("Unhandled exception in protocol " + getProtoName() +" ("+ getProtoId() +"-childLoop1) " + e, e);
    //            e.printStackTrace();
    //        }
    //    }
    //}
    //private  void   childLoop2(){
    //    while (true) {
    //        try {
    //            InternalEvent pe = this.childQueue2.take();
    //            switch (pe.getType()) {
    //                case MESSAGE_IN_EVENT:
    //                    this.handleMessageIn((MessageInEvent) pe);
    //                    break;
    //                case MESSAGE_FAILED_EVENT:
    //                    this.handleMessageFailed((MessageFailedEvent) pe);
    //                    break;
    //                case MESSAGE_SENT_EVENT:
    //                    this.handleMessageSent((MessageSentEvent) pe);
    //                    break;
    //                case TIMER_EVENT:
    //                    this.handleTimer((TimerEvent) pe);
    //                    break;
    //                case NOTIFICATION_EVENT:
    //                    this.handleNotification((NotificationEvent) pe);
    //                    break;
    //                case IPC_EVENT:
    //                    IPCEvent i = (IPCEvent) pe;
    //                    switch (i.getIpc().getType()) {
    //                        case REPLY:
    //                            handleReply((ProtoReply) i.getIpc(), i.getSenderID());
    //                            break;
    //                        case REQUEST:
    //                            handleRequest((ProtoRequest) i.getIpc(), i.getSenderID());
    //                            break;
    //                        default:
    //                            throw new AssertionError("Ups");
    //                    }
    //                    break;
    //                case CUSTOM_CHANNEL_EVENT:
    //                    this.handleChannelEvent((CustomChannelEvent) pe);
    //                    break;
    //                default:
    //                    throw new AssertionError("Unexpected event received by babel. protocol "
    //                            + protoId + " (" + protoName + "-childLoop2)");
    //            }
    //        } catch (Exception e) {
    //            logger.error("Unhandled exception in protocol " + getProtoName() +" ("+ getProtoId() +"-childLoop2) " + e, e);
    //            e.printStackTrace();
    //        }
    //    }
    //}
    //private  void   childLoop3(){
    //    while (true) {
    //        try {
    //            InternalEvent pe = this.childQueue3.take();
    //            switch (pe.getType()) {
    //                case MESSAGE_IN_EVENT:
    //                    this.handleMessageIn((MessageInEvent) pe);
    //                    break;
    //                case MESSAGE_FAILED_EVENT:
    //                    this.handleMessageFailed((MessageFailedEvent) pe);
    //                    break;
    //                case MESSAGE_SENT_EVENT:
    //                    this.handleMessageSent((MessageSentEvent) pe);
    //                    break;
    //                case TIMER_EVENT:
    //                    this.handleTimer((TimerEvent) pe);
    //                    break;
    //                case NOTIFICATION_EVENT:
    //                    this.handleNotification((NotificationEvent) pe);
    //                    break;
    //                case IPC_EVENT:
    //                    IPCEvent i = (IPCEvent) pe;
    //                    switch (i.getIpc().getType()) {
    //                        case REPLY:
    //                            handleReply((ProtoReply) i.getIpc(), i.getSenderID());
    //                            break;
    //                        case REQUEST:
    //                            handleRequest((ProtoRequest) i.getIpc(), i.getSenderID());
    //                            break;
    //                        default:
    //                            throw new AssertionError("Ups");
    //                    }
    //                    break;
    //                case CUSTOM_CHANNEL_EVENT:
    //                    this.handleChannelEvent((CustomChannelEvent) pe);
    //                    break;
    //                default:
    //                    throw new AssertionError("Unexpected event received by babel. protocol "
    //                            + protoId + " (" + protoName + "-childLoop3)");
    //            }
    //        } catch (Exception e) {
    //            logger.error("Unhandled exception in protocol " + getProtoName() +" ("+ getProtoId() +"-childLoop3) " + e, e);
    //            e.printStackTrace();
    //        }
    //    }
    //}
    //private  void   childLoop4(){
    //    while (true) {
    //        try {
    //            InternalEvent pe = this.childQueue4.take();
    //            switch (pe.getType()) {
    //                case MESSAGE_IN_EVENT:
    //                    this.handleMessageIn((MessageInEvent) pe);
    //                    break;
    //                case MESSAGE_FAILED_EVENT:
    //                    this.handleMessageFailed((MessageFailedEvent) pe);
    //                    break;
    //                case MESSAGE_SENT_EVENT:
    //                    this.handleMessageSent((MessageSentEvent) pe);
    //                    break;
    //                case TIMER_EVENT:
    //                    this.handleTimer((TimerEvent) pe);
    //                    break;
    //                case NOTIFICATION_EVENT:
    //                    this.handleNotification((NotificationEvent) pe);
    //                    break;
    //                case IPC_EVENT:
    //                    IPCEvent i = (IPCEvent) pe;
    //                    switch (i.getIpc().getType()) {
    //                        case REPLY:
    //                            handleReply((ProtoReply) i.getIpc(), i.getSenderID());
    //                            break;
    //                        case REQUEST:
    //                            handleRequest((ProtoRequest) i.getIpc(), i.getSenderID());
    //                            break;
    //                        default:
    //                            throw new AssertionError("Ups");
    //                    }
    //                    break;
    //                case CUSTOM_CHANNEL_EVENT:
    //                    this.handleChannelEvent((CustomChannelEvent) pe);
    //                    break;
    //                default:
    //                    throw new AssertionError("Unexpected event received by babel. protocol "
    //                            + protoId + " (" + protoName + "-childLoop4)");
    //            }
    //        } catch (Exception e) {
    //            logger.error("Unhandled exception in protocol " + getProtoName() +" ("+ getProtoId() +"-childLoop4) " + e, e);
    //            e.printStackTrace();
    //        }
    //    }
    //}
    private void ipcLoop() {
        while (true) {
            try {
                InternalEvent pe = this.ipcqueue.take();
                //metrics.totalEventsCount++;
                if (logger.isDebugEnabled())
                    logger.debug("Handling event: " + pe);
                switch (pe.getType()) {
                    case IPC_EVENT:
                        IPCEvent i = (IPCEvent) pe;
                        switch (i.getIpc().getType()) {
                            case REPLY:
                                //metrics.repliesCount++;
                                handleReply((ProtoReply) i.getIpc(), i.getSenderID());
                                break;
                            case REQUEST:
                                metrics.requestsCount++;
                                handleRequest((ProtoRequest) i.getIpc(), i.getSenderID());
                                break;
                            default:
                                throw new AssertionError("Ups");
                        }
                        break;
                    default:
                        throw new AssertionError("Unexpected event received by babel. protocol "
                                + protoId + " (" + protoName + "-mainLoop)");
                }
            } catch (Exception e) {
                logger.error("Unhandled exception in protocol " + getProtoName() +" ("+ getProtoId() +"-mainLoop) " + e, e);
                e.printStackTrace();
            }
        }
    }
    
    
    
    
    //TODO try catch (ClassCastException)
    private void handleMessageIn(MessageInEvent m) {
        BabelMessage msg = m.getMsg();
        MessageInHandler h = getChannelOrThrow(m.getChannelId()).messageInHandlers.get(msg.getMessage().getId());
        if (h != null)
            h.receive(msg.getMessage(), m.getFrom(), msg.getSourceProto(), m.getChannelId());
        else
            logger.warn("Discarding unexpected message (id " + msg.getMessage().getId() + "): " + m);
    }

    private void handleMessageFailed(MessageFailedEvent e) {
        BabelMessage msg = e.getMsg();
        MessageFailedHandler h = getChannelOrThrow(e.getChannelId()).messageFailedHandlers.get(msg.getMessage().getId());
        if (h != null)
            h.onMessageFailed(msg.getMessage(), e.getTo(), msg.getDestProto(), e.getCause(), e.getChannelId());
        else if (logger.isDebugEnabled())
            logger.debug("Discarding unhandled message failed event " + e);
    }

    private void handleMessageSent(MessageSentEvent e) {
        BabelMessage msg = e.getMsg();
        MessageSentHandler h = getChannelOrThrow(e.getChannelId()).messageSentHandlers.get(msg.getMessage().getId());
        if (h != null)
            h.onMessageSent(msg.getMessage(), e.getTo(), msg.getDestProto(), e.getChannelId());
    }

    private void handleChannelEvent(CustomChannelEvent m) {
        ChannelEventHandler h = getChannelOrThrow(m.getChannelId()).channelEventHandlers.get(m.getEvent().getId());
        if (h != null)
            h.handleEvent(m.getEvent(), m.getChannelId());
        else if (logger.isDebugEnabled())
            logger.debug("Discarding unhandled channel event (id " + m.getChannelId() + "): " + m);
    }

    private void handleTimer(TimerEvent t) {
        TimerHandler h = this.timerHandlers.get(t.getTimer().getId());
        if (h != null)
            h.uponTimer(t.getTimer().clone(), t.getUuid());
        else
            logger.warn("Discarding unexpected timer (id " + t.getTimer().getId() + "): " + t);
    }

    private void handleNotification(NotificationEvent n) {
        NotificationHandler h = this.notificationHandlers.get(n.getNotification().getId());
        if (h != null)
            h.uponNotification(n.getNotification(), n.getEmitterID());
        else
            logger.warn("Discarding unexpected notification (id " + n.getNotification().getId() + "): " + n);
    }

    private void handleRequest(ProtoRequest r, short from) {
        RequestHandler h = this.requestHandlers.get(r.getId());
        if (h != null)
            h.uponRequest(r, from);
        else {
            logger.warn("Discarding unexpected request (id " + r.getId() + "): " + r);
            logger.warn( "存在哪些"+this.requestHandlers.entrySet());
        }
    }

    private void handleReply(ProtoReply r, short from) {
        ReplyHandler h = this.replyHandlers.get(r.getId());
        if (h != null)
            h.uponReply(r, from);
        else
            logger.warn("Discarding unexpected reply (id " + r.getId() + "): " + r);
    }

    private static class ChannelHandlers {
        private final Map<Short, MessageInHandler<? extends ProtoMessage>> messageInHandlers;
        private final Map<Short, MessageSentHandler<? extends ProtoMessage>> messageSentHandlers;
        private final Map<Short, MessageFailedHandler<? extends ProtoMessage>> messageFailedHandlers;
        private final Map<Short, ChannelEventHandler<? extends ChannelEvent>> channelEventHandlers;

        public ChannelHandlers() {
            this.messageInHandlers = new HashMap<>();
            this.messageSentHandlers = new HashMap<>();
            this.messageFailedHandlers = new HashMap<>();
            this.channelEventHandlers = new HashMap<>();
        }
    }

    public static class ProtocolMetrics {
        private long totalEventsCount, messagesInCount, messagesFailedCount, messagesSentCount, timersCount,
                notificationsCount, requestsCount, repliesCount, customChannelEventsCount;

        @Override
        public String toString() {
            return "ProtocolMetrics{" +
                    "totalEvents=" + totalEventsCount +
                    ", messagesIn=" + messagesInCount +
                    ", messagesFailed=" + messagesFailedCount +
                    ", messagesSent=" + messagesSentCount +
                    ", timers=" + timersCount +
                    ", notifications=" + notificationsCount +
                    ", requests=" + requestsCount +
                    ", replies=" + repliesCount +
                    ", customChannelEvents=" + customChannelEventsCount +
                    '}';
        }

        public void reset() {
            totalEventsCount = messagesFailedCount = messagesInCount = messagesSentCount = timersCount =
                    notificationsCount = repliesCount = requestsCount = customChannelEventsCount = 0;
        }

        public long getCustomChannelEventsCount() {
            return customChannelEventsCount;
        }

        public long getMessagesFailedCount() {
            return messagesFailedCount;
        }

        public long getMessagesInCount() {
            return messagesInCount;
        }

        public long getMessagesSentCount() {
            return messagesSentCount;
        }

        public long getNotificationsCount() {
            return notificationsCount;
        }

        public long getRepliesCount() {
            return repliesCount;
        }

        public long getRequestsCount() {
            return requestsCount;
        }

        public long getTimersCount() {
            return timersCount;
        }

        public long getTotalEventsCount() {
            return totalEventsCount;
        }
    }
}