package pt.unl.fct.di.novasys.babel.core;


import pt.unl.fct.di.novasys.babel.initializers.*;
import pt.unl.fct.di.novasys.babel.internal.BabelMessage;
import pt.unl.fct.di.novasys.babel.internal.IPCEvent;
import pt.unl.fct.di.novasys.babel.internal.NotificationEvent;
import pt.unl.fct.di.novasys.babel.internal.TimerEvent;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import pt.unl.fct.di.novasys.babel.exceptions.NoSuchProtocolException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.babel.metrics.MetricsManager;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.channel.IChannel;
import pt.unl.fct.di.novasys.channel.accrual.AccrualChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleClientChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleServerChannel;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.commons.lang3.tuple.Triple;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Babel class provides applications with a Runtime that supports
 * the execution of protocols.
 *
 * <p> An example of how to use the class follows:
 *
 * <pre>
 *         Babel babel = Babel.getInstance(); //initialize babel
 *         Properties configProps = babel.loadConfig("network_config.properties", args);
 *         INetwork net = babel.getNetworkInstance();
 *
 *         //Define protocols
 *         ProtocolA protoA = new ProtocolA(net);
 *         protoA.init(configProps);
 *
 *         ProtocolB protoB = new ProtocolB(net);
 *         protoB.init(configProps);
 *
 *         //Register protocols
 *         babel.registerProtocol(protoA);
 *         babel.registerProtocol(protoB);
 *
 *         //subscribe to notifications
 *         protoA.subscribeNotification(protoA.NOTIFICATION_ID, this);
 *
 *         //start babel runtime
 *         babel.start();
 *
 *         //Application Logic
 *
 * </pre>
 * <p>
 * For more information on protocol implementation with Babel:
 *
 * @see GenericProtocol
 */
public class Babel {

    // TODO: 2023/6/21   Babel只是传递消息，不涉及处理，应该对性能影响不大,或许可以对
    //  修改意见 Babel从单线程改成多线程
    private static Babel system;

    // 单例模式：它是个运行框架
    /**
     * Returns the instance of the Babel Runtime
     *
     * @return the Babel instance
     */
    public static synchronized Babel getInstance() {
        if (system == null)
            system = new Babel();
        return system;
    }

    
    
    
    //Protocols 注册了哪些协议，这个框架中拥有哪些协议
    private final Map<Short, GenericProtocol> protocolMap;
    private final Map<String, GenericProtocol> protocolByNameMap;
    
    
    
    
    //注册哪些订阅
    private final Map<Short, Set<GenericProtocol>> subscribers;
    
    
    
    //Timers
    private final Map<Long, TimerEvent> allTimers;
    // 一份协议一个timer
    private final PriorityBlockingQueue<TimerEvent>  timerQueue;
    private final PriorityBlockingQueue<TimerEvent>  timerQueue2;
    private final PriorityBlockingQueue<TimerEvent>  timerQueue3;
    private final PriorityBlockingQueue<TimerEvent>  timerQueue4;
    private final  Thread    timersThread;
    private  final  Thread   timersThread2;
    private  final  Thread   timersThread3;
    private  final  Thread   timersThread4;
    private final AtomicLong timersCounter;
    
    
    
    
    //Channels的初始化工厂: 这里用到了TCP通道 和其他类型的通道
    private final Map<String, ChannelInitializer<? extends IChannel<BabelMessage>>> initializers;


    
    
    
    // 代表着网络层的抽象，有通道    通道的接收     序列化/反序列化器
    private final Map<Integer,
            Triple<IChannel<BabelMessage>, ChannelToProtoForwarder, BabelMessageSerializer>> channelMap;
    private final AtomicInteger channelIdGenerator;

    
    
    
    private long startTime;
    private boolean started = false;

    
    
    private Babel() {
        //Protocols
        this.protocolMap = new ConcurrentHashMap<>();
        this.protocolByNameMap = new ConcurrentHashMap<>();
        this.subscribers = new ConcurrentHashMap<>();

        //Timers
        allTimers = new HashMap<>();
        timerQueue = new PriorityBlockingQueue<>();
        timerQueue2 = new PriorityBlockingQueue<>();
        timerQueue3 = new PriorityBlockingQueue<>();
        timerQueue4 = new PriorityBlockingQueue<>();
        timersCounter = new AtomicLong();
        timersThread = new Thread(this::timerLoop);
        timersThread2 = new Thread(this::timerLoop2);
        timersThread3 = new Thread(this::timerLoop3);
        timersThread4 = new Thread(this::timerLoop4);
        
        //Channels
        channelMap = new ConcurrentHashMap<>();
        channelIdGenerator = new AtomicInteger(0);
        this.initializers = new ConcurrentHashMap<>();
        // 注册了四种通道类型
        registerChannelInitializer(SimpleClientChannel.NAME, new SimpleClientChannelInitializer());
        registerChannelInitializer(SimpleServerChannel.NAME, new SimpleServerChannelInitializer());
        registerChannelInitializer(TCPChannel.NAME, new TCPChannelInitializer());
        registerChannelInitializer(AccrualChannel.NAME, new AccrualChannelInitializer());

        //registerChannelInitializer("Ackos", new AckosChannelInitializer());
        //registerChannelInitializer(MultithreadedTCPChannel.NAME, new MultithreadedTCPChannelInitializer());
    }

    
    
    /**
     * Register a protocol in Babel
     *
     * @param p the protocol to registered
     * @throws ProtocolAlreadyExistsException if a protocol with the same id or name has already been registered
     */
    public void registerProtocol(GenericProtocol p) throws ProtocolAlreadyExistsException {
        GenericProtocol old = protocolMap.putIfAbsent(p.getProtoId(), p);
        if (old != null) throw new ProtocolAlreadyExistsException(
                "Protocol conflicts on id with protocol: id=" + p.getProtoId() + ":name=" + protocolMap.get(
                        p.getProtoId()).getProtoName());
        old = protocolByNameMap.putIfAbsent(p.getProtoName(), p);
        if (old != null) {
            protocolMap.remove(p.getProtoId());
            throw new ProtocolAlreadyExistsException(
                    "Protocol conflicts on name: " + p.getProtoName() + " (id: " + this.protocolByNameMap.get(
                            p.getProtoName()).getProtoId() + ")");
        }
    }

    

    // todo 对系统中的时钟进行模拟，这个真的对吗？timerQueue添加过程中，不是按照顺序添加的
    private void timerLoop() {
        while (true) {
            long now = getMillisSinceStart();
            
            TimerEvent tE = timerQueue.peek();

            long toSleep = tE != null ? tE.getTriggerTime() - now : Long.MAX_VALUE;

            if (toSleep <= 0) {//
                TimerEvent t = timerQueue.remove();
                //Deliver
                t.getConsumer().deliverTimer(t);
                if (t.isPeriodic()) {
                    t.setTriggerTime(now + t.getPeriod());
                    timerQueue.add(t);
                }
            } else {
                try {
                    Thread.sleep(toSleep);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
    
    private void timerLoop2() {
        while (true) {
            long now = getMillisSinceStart();

            TimerEvent tE = timerQueue2.peek();

            long toSleep = tE != null ? tE.getTriggerTime() - now : Long.MAX_VALUE;

            if (toSleep <= 0) {//
                TimerEvent t = timerQueue2.remove();
                //Deliver
                t.getConsumer().deliverTimer(t);
                if (t.isPeriodic()) {
                    t.setTriggerTime(now + t.getPeriod());
                    timerQueue2.add(t);
                }
            } else {
                try {
                    Thread.sleep(toSleep);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void timerLoop3() {
        while (true) {
            long now = getMillisSinceStart();

            TimerEvent tE = timerQueue3.peek();

            long toSleep = tE != null ? tE.getTriggerTime() - now : Long.MAX_VALUE;

            if (toSleep <= 0) {//
                TimerEvent t = timerQueue3.remove();
                //Deliver
                t.getConsumer().deliverTimer(t);
                if (t.isPeriodic()) {
                    t.setTriggerTime(now + t.getPeriod());
                    timerQueue3.add(t);
                }
            } else {
                try {
                    Thread.sleep(toSleep);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
    
    
    private  void  timerLoop4(){
        while (true) {
            long now = getMillisSinceStart();

            TimerEvent tE = timerQueue4.peek();

            long toSleep = tE != null ? tE.getTriggerTime() - now : Long.MAX_VALUE;

            if (toSleep <= 0) {//
                TimerEvent t = timerQueue4.remove();
                //Deliver
                t.getConsumer().deliverTimer(t);
                if (t.isPeriodic()) {
                    t.setTriggerTime(now + t.getPeriod());
                    timerQueue4.add(t);
                }
            } else {
                try {
                    Thread.sleep(toSleep);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    
    
    /**
     * Begins the execution of all protocols registered in Babel
     */
    public void start() {
        startTime = System.currentTimeMillis();
        started = true;
        
        MetricsManager.getInstance().start();
        timersThread.start();
        timersThread2.start();
        timersThread3.start();
        timersThread4.start();
        
        protocolMap.values().forEach(GenericProtocol::start);
    }

    
    
    
    
    
    
    // ----------------------------- NETWORK
    
    /**
     * 注册几种通道类型的初始化器Registers a new channel in babel
     *
     * @param name        the channel name
     * @param initializer the channel initializer
     */
    public void registerChannelInitializer(String name,
                                           ChannelInitializer<? extends IChannel<BabelMessage>> initializer) {
        ChannelInitializer<? extends IChannel<BabelMessage>> old = initializers.putIfAbsent(name, initializer);
        if (old != null) {
            throw new IllegalArgumentException("Initializer for channel with name " + name +
                    " already registered: " + old);
        }
    }


    
    
    
    // 创建一个通道：如TCP通道
  
    /**
     * Creates a channel for a protocol
     * Called by {@link GenericProtocol}. Do not evoke directly.
     *
     * @param channelName the name of the channel to create
     * @param protoId     the protocol numeric identifier
     * @param props       the properties required by the channel
     * @return the channel Id
     * @throws IOException if channel creation fails
     */
    int createChannel(String channelName, short protoId, Properties props)
            throws IOException {
        ChannelInitializer<? extends IChannel<?>> initializer = initializers.get(channelName);
        if (initializer == null)
            throw new IllegalArgumentException("Channel initializer not registered: " + channelName);

        int channelId = channelIdGenerator.incrementAndGet();
        BabelMessageSerializer serializer = new BabelMessageSerializer(new ConcurrentHashMap<>());
        ChannelToProtoForwarder forwarder = new ChannelToProtoForwarder(channelId);
        IChannel<BabelMessage> newChannel = initializer.initialize(serializer, forwarder, props, protoId);
        channelMap.put(channelId, Triple.of(newChannel, forwarder, serializer));
        return channelId;
    }
    
    
    //注册 通道到protocol的转发 ，哪个协议是对这个通道来的消息的消费者
    /**
     * Registers interest in receiving events from a channel.
     *
     * @param consumerProto the protocol that will receive events generated by the new channel
     *                      Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void registerChannelInterest(int channelId, short protoId, GenericProtocol consumerProto) {
        ChannelToProtoForwarder forwarder = channelMap.get(channelId).getMiddle();
        forwarder.addConsumer(protoId, consumerProto);
    }

    
    
    // 注册消息的序列化和反序列化
    /**
     * Registers a (de)serializer for a message type.
     * Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void registerSerializer(int channelId, short msgCode, ISerializer<? extends ProtoMessage> serializer) {
        Triple<IChannel<BabelMessage>, ChannelToProtoForwarder, BabelMessageSerializer> channelEntry =
                channelMap.get(channelId);
        if (channelEntry == null)
            throw new AssertionError("Registering serializer in non-existing channelId " + channelId);
        channelEntry.getRight().registerProtoSerializer(msgCode, serializer);
    }



    




    /**
     * Opens a connection to a peer in the given channel.
     * Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void openConnection(int channelId, Host target) {
        Triple<IChannel<BabelMessage>, ChannelToProtoForwarder, BabelMessageSerializer> channelEntry =
                channelMap.get(channelId);
        if (channelEntry == null)
            throw new AssertionError("Opening connection in non-existing channelId " + channelId);
        channelEntry.getLeft().openConnection(target);
    }

    /**
     * Closes a connection to a peer in a given channel.
     * Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void closeConnection(int channelId, Host target, int connection) {
        Triple<IChannel<BabelMessage>, ChannelToProtoForwarder, BabelMessageSerializer> channelEntry =
                channelMap.get(channelId);
        if (channelEntry == null)
            throw new AssertionError("Closing connection in non-existing channelId " + channelId);
        channelEntry.getLeft().closeConnection(target, connection);
    }

    
    /**
     * Sends a message to a peer using the given channel and connection.
     * Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void sendMessage(int channelId, int connection, BabelMessage msg, Host target) {
        Triple<IChannel<BabelMessage>, ChannelToProtoForwarder, BabelMessageSerializer> channelEntry =
                channelMap.get(channelId);
        if (channelEntry == null)
            throw new AssertionError("Sending message to non-existing channelId " + channelId);
        channelEntry.getLeft().sendMessage(msg, target, connection);
    }
    
    
    
    
    
    

    
    
    
    
    
    
    
    
    
    
    // ----------------------------- REQUEST / REPLY / NOTIFY

    /**
     * Send a request/reply to a protocol
     * Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void sendIPC(IPCEvent ipc) throws NoSuchProtocolException {
        GenericProtocol gp = protocolMap.get(ipc.getDestinationID());
        if (gp == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(ipc.getDestinationID()).append(" not executing.");
            sb.append("Executing protocols: [");
            protocolMap.forEach((id, p) -> sb.append(id).append(" - ").append(p.getProtoName()).append(", "));
            sb.append("]");
            throw new NoSuchProtocolException(sb.toString());
        }
        gp.deliverIPC(ipc);
    }

    

    // ----------------------------- NOTIFY
    
    /**
     * Subscribes a protocol to a notification
     * Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void subscribeNotification(short nId, GenericProtocol consumer) {
        subscribers.computeIfAbsent(nId, k -> ConcurrentHashMap.newKeySet()).add(consumer);
    }

    /**
     * Unsubscribes a protocol from a notification
     * Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void unsubscribeNotification(short nId, GenericProtocol consumer) {
        subscribers.getOrDefault(nId, Collections.emptySet()).remove(consumer);
    }

    
    //-----------------------触发相应的订阅
    
    /**
     * Triggers a notification, delivering to all subscribed protocols
     * Called by {@link GenericProtocol}. Do not evoke directly.
     */
    void triggerNotification(NotificationEvent n) {
        for (GenericProtocol c : subscribers.getOrDefault(n.getNotification().getId(), Collections.emptySet())) {
            c.deliverNotification(n);
        }
    }

    
    
    
    
    
    
    // ---------------------------- TIMERS 每个协议一个timer线程 

    /**
     * Setups a periodic timer to be monitored by Babel
     * Called by {@link GenericProtocol}. Do not evoke directly.
     *
     * @param consumer the protocol that setup the periodic timer
     * @param first    the amount of time until the first trigger of the timer event
     * @param period   the periodicity of the timer event
     */
    long setupPeriodicTimer(ProtoTimer t, GenericProtocol consumer,short protoid, long first, long period) {
        long id = timersCounter.incrementAndGet();
        TimerEvent newTimer = new TimerEvent(t, id, consumer,
                getMillisSinceStart() + first, true, period);
        allTimers.put(newTimer.getUuid(), newTimer);
        if (protoid==100){
            if (t.getId()==101){// 是batch定时发送时钟
                timerQueue.add(newTimer);
                timersThread.interrupt();
            }
        }else if (protoid==200){
            if (t.getId()==202){// leader超时时钟  5000ms
                timerQueue4.add(newTimer);
                timersThread4.interrupt();
            }
            if (t.getId()==203){// noop 100ms
                timerQueue2.add(newTimer);
                timersThread2.interrupt();
            }
        }else {
            timerQueue3.add(newTimer);
            timersThread3.interrupt();
        }
        return id;
    }

    /**
     * Setups a timer to be monitored by Babel
     * Called by {@link GenericProtocol}. Do not evoke directly.
     *
     * @param consumer the protocol that setup the timer
     * @param timeout  the amount of time until the timer event is triggered
     */
    long setupTimer(ProtoTimer t, GenericProtocol consumer, short protoid,long timeout) {
        long id = timersCounter.incrementAndGet();
        TimerEvent newTimer = new TimerEvent(t, id, consumer,
                getMillisSinceStart() + timeout, false, -1);
        allTimers.put(newTimer.getUuid(), newTimer);
        if (protoid==100){
            if (t.getId()==204){ // 是重连挂载节点时钟  1000 ms
                timerQueue3.add(newTimer);
                timersThread3.interrupt();
            }
        }else if (protoid==200){
            if (t.getId()==201){// jointime时钟  3000ms
                timerQueue4.add(newTimer);
                timersThread4.interrupt();
            }
            if (t.getId()==205){// StateTransferTimer时钟  5000ms
                timerQueue4.add(newTimer);
                timersThread4.interrupt();
            }
            if (t.getId()==204){// 重连时钟  1000ms
                timerQueue3.add(newTimer);
                timersThread3.interrupt();
            }
        }else {
            if (t.getId()==304){// 重新连接其他节点Data层的时钟 1000ms
                timerQueue3.add(newTimer);
                timersThread3.interrupt();
            }
            if (t.getId()==206){//是刷新闹钟     100ms
                timerQueue2.add(newTimer);
                timersThread2.interrupt();
            }
        }
        return id;
    }

    /**
     * Cancels a timer that was being monitored by Babel
     * Babel will forget that the timer exists
     * Called by {@link GenericProtocol}. Do not evoke directly.
     *
     * @param timerID the unique id of the timer event to be canceled
     * @return the timer event or null if it was not being monitored by Babel
     */
    ProtoTimer cancelTimer(long timerID,short protoid) {
        TimerEvent tE = allTimers.remove(timerID);
        if (tE == null)
            return null;
        if (protoid==100){// 不会出现cancel时钟
            timerQueue.remove(tE);
            timersThread.interrupt();
        }else if (protoid==200){
            if (tE.getTimer().getId()==202){// leader超时时钟  5000ms
                timerQueue4.remove(tE);
                timersThread4.interrupt();
            }
            if (tE.getTimer().getId()==201){// jointime时钟  3000ms
       
                timerQueue4.remove(tE);
                timersThread4.interrupt();
            }
            if (tE.getTimer().getId()==205){// StateTransferTimer时钟  5000ms
                timerQueue4.remove(tE);
                timersThread4.interrupt();
            }
        }else {
            if (tE.getTimer().getId()==206){//是刷新闹钟     100ms
                timerQueue2.remove(tE);
                timersThread2.interrupt();
            }
        }
        return tE.getTimer();
    }

    
    
    
    
    
    
    // ---------------------------- CONFIG----
    
    
    /**
     * Reads either the default or the given properties file (the file can be given with the argument -config)
     * Builds a configuration file with the properties from the file and then merges ad-hoc properties given
     * in the arguments.
     * <p>
     * Argument properties should be provided as:   propertyName=value
     *
     * @param defaultConfigFile the path to the default properties file
     * @param args              console parameters
     * @return the configurations built
     * @throws IOException               if the provided file does not exist
     * @throws InvalidParameterException if the console parameters are not in the format: prop=value
     */
    public static Properties loadConfig(String[] args, String defaultConfigFile)
            throws IOException, InvalidParameterException {

        List<String> argsList = new ArrayList<>();
        Collections.addAll(argsList, args);
        String configFile = extractConfigFileFromArguments(argsList, defaultConfigFile);

        Properties configuration = new Properties();
        if (configFile != null)
            configuration.load(new FileInputStream(configFile));
        //Override with launch parameter props
        for (String arg : argsList) {
            String[] property = arg.split("=");
            if (property.length == 2)
                configuration.setProperty(property[0], property[1]);
            else
                throw new InvalidParameterException("Unknown parameter: " + arg);
        }
        return configuration;
    }
    
    // 返回配置文件名字：在参数指定的配置文件的优先级大于default配置文件
    private static String extractConfigFileFromArguments(List<String> args, String defaultConfigFile) {
        String config = defaultConfigFile;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String param = iter.next();
            if (param.equals("-conf")) {
                if (iter.hasNext()) {
                    iter.remove();
                    config = iter.next();
                    iter.remove();
                }
                break;
            }
        }
        return config;
    }
    
    // 得到整个系统的运行时间
    public long getMillisSinceStart() {
        return started ? System.currentTimeMillis() - startTime : 0;
    }

}
