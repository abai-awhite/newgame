package server.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 事件总线（骨架）：事件发布/订阅。
 *
 * <p>支持按事件类型订阅处理器，post 时派发。本期仅保留结构，
 * 事件类型（方块破坏/放置、玩家加入/离开、tick、存档前后）后期定义具体事件类。</p>
 */
public class ModEventBus {

    private final Map<String, List<Consumer<ModEvent>>> handlers = new HashMap<>();

    /** 订阅指定类型事件。 */
    public void subscribe(String eventType, Consumer<ModEvent> handler) {
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    }

    /** 发布事件（派发给所有订阅者）。 */
    public void post(ModEvent event) {
        List<Consumer<ModEvent>> list = handlers.get(event.getType());
        if (list == null) return;
        for (Consumer<ModEvent> h : new ArrayList<>(list)) {
            h.accept(event);
        }
    }

    public List<String> getSubscribedTypes() {
        return new ArrayList<>(handlers.keySet());
    }
}
