package io.quarkus.websockets;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerEndpoint("/chat/{username}")
@ApplicationScoped
public class ChatServer {
    private static final Logger log = LoggerFactory.getLogger(ChatServer.class);

    @Inject
    EntityManager em;

    Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    @Transactional
    public void onOpen(Session session, @PathParam("username") String username) {
        log.info(".. OnOpen ... " + username + " >> " + session);
        sessions.put(username, session);
        ChatEntity entity = new ChatEntity();
        entity.setUser(username);
        entity.setMsg("OnOpen");
        em.persist(entity);
        log.info("Entity = " + entity);
    }

    @OnClose
    public void onClose(Session session, @PathParam("username") String username) {
        log.info(".. OnClose ... " + username + " >> " + session);
        sessions.remove(username);
        broadcast("User " + username + " left");
    }

    @OnError
    public void onError(Session session, @PathParam("username") String username, Throwable throwable) {
        log.error(".. OnError ... " + username + " >> " + session, throwable);
        sessions.remove(username);
        broadcast("User " + username + " left on error: " + throwable);
    }

    @OnMessage
    @Transactional
    public void onMessage(String message, @PathParam("username") String username) {
        log.info(".. OnMessage ... " + username + " >> " + message);
        if (message.equalsIgnoreCase("_ready_")) {
            broadcast("User " + username + " joined");
        } else {
            broadcast(">> " + username + ": " + message);
        }
        ChatEntity entity = new ChatEntity();
        entity.setUser(username);
        entity.setMsg("OnOpen");
        em.persist(entity);
        log.info("Entity = " + entity);
    }

    private void broadcast(String message) {
        sessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(message, result -> {
                if (result.getException() != null) {
                    System.out.println("Unable to send message: " + result.getException());
                }
            });
        });
    }

}
