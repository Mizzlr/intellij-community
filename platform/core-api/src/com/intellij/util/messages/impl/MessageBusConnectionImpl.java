// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.messages.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.SmartFMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;

public class MessageBusConnectionImpl implements MessageBusConnection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.messages.impl.MessageBusConnectionImpl");

  private final MessageBusImpl myBus;
  @SuppressWarnings("SSBasedInspection")
  private final ThreadLocal<Queue<Message>> myPendingMessages = MessageBusImpl.createThreadLocalQueue();

  private MessageHandler myDefaultHandler;
  private volatile SmartFMap<Topic, Object> mySubscriptions = SmartFMap.emptyMap();

  public MessageBusConnectionImpl(@NotNull MessageBusImpl bus) {
    myBus = bus;
  }

  @Override
  public <L> void subscribe(@NotNull Topic<L> topic, @NotNull L handler) throws IllegalStateException {
    synchronized (myPendingMessages) {
      if (mySubscriptions.get(topic) != null) {
        throw new IllegalStateException("Subscription to " + topic + " already exists");
      }
      mySubscriptions = mySubscriptions.plus(topic, handler);
    }
    myBus.notifyOnSubscription(this, topic);
  }

  @Override
  public <L> void subscribe(@NotNull Topic<L> topic) throws IllegalStateException {
    MessageHandler defaultHandler = myDefaultHandler;
    if (defaultHandler == null) {
      throw new IllegalStateException("Connection must have default handler installed prior to any anonymous subscriptions. "
                                      + "Target topic: " + topic);
    }
    if (topic.getListenerClass().isInstance(defaultHandler)) {
      throw new IllegalStateException("Can't subscribe to the topic '" + topic + "'. Default handler has incompatible type - expected: '" +
                                      topic.getListenerClass() + "', actual: '" + defaultHandler.getClass() + "'");
    }

    //noinspection unchecked
    subscribe(topic, (L)defaultHandler);
  }

  @Override
  public void setDefaultHandler(MessageHandler handler) {
    myDefaultHandler = handler;
  }

  @Override
  public void dispose() {
    myPendingMessages.get();
    myPendingMessages.remove();
    myBus.notifyConnectionTerminated(this);
  }

  @Override
  public void disconnect() {
    Disposer.dispose(this);
  }

  @Override
  public void deliverImmediately() {
    Queue<Message> messages = myPendingMessages.get();
    while (!messages.isEmpty()) {
      myBus.deliverSingleMessage();
    }
  }

  void deliverMessage(@NotNull Message message) {
    final Message messageOnLocalQueue = myPendingMessages.get().poll();
    assert messageOnLocalQueue == message;

    final Topic topic = message.getTopic();
    final Object handler = mySubscriptions.get(topic);

    try {
      Method listenerMethod = message.getListenerMethod();

      if (handler == myDefaultHandler) {
        myDefaultHandler.handle(listenerMethod, message.getArgs());
      }
      else {
        long startTime = System.nanoTime();
        listenerMethod.invoke(handler, message.getArgs());
        long endTime = System.nanoTime();
        myBus.notifyMessageDeliveryListener(topic, listenerMethod.getName(), handler, endTime - startTime);
      }
    }
    catch (AbstractMethodError e) {
      //Do nothing. This listener just does not implement something newly added yet.
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (InvocationTargetException e) {
      if (e.getCause() instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)e.getCause();
      }
      LOG.error(e.getCause() == null ? e : e.getCause());
    }
    catch (Throwable e) {
      LOG.error(e.getCause() == null ? e : e.getCause());
    }
  }

  void scheduleMessageDelivery(@NotNull Message message) {
    myPendingMessages.get().offer(message);
  }

  boolean containsMessage(@NotNull Topic topic) {
    Queue<Message> pendingMessages = myPendingMessages.get();
    if (pendingMessages.isEmpty()) return false;

    for (Message message : pendingMessages) {
      if (message.getTopic() == topic) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return mySubscriptions.toString();
  }

  @NotNull
  MessageBusImpl getBus() {
    return myBus;
  }
}
