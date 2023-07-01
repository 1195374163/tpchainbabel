package pt.unl.fct.di.novasys.babel.generic;

/**
 * Abstract Message class to be extended by protocol-specific messages.
 */
public abstract class ProtoMessage {

    private final short id;
        
    //分发消息的处理线程的id;设为public
    public  short   threadid=-1;

    
    public ProtoMessage(short id){
        this.id = id;
    }

    
    public short getId() {
        return id;
    }
    
    public  short getThreadid(){
        return threadid;
    }
}
