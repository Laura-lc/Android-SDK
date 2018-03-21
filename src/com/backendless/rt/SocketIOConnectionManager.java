package com.backendless.rt;

import com.backendless.Backendless;
import com.backendless.HeadersManager;
import com.backendless.async.callback.Result;
import com.backendless.persistence.local.UserTokenStorageFactory;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.logging.Logger;

abstract class SocketIOConnectionManager
{
  private static final Logger logger = Logger.getLogger( "SocketIOConnectionManager" );

  private static final int INITIAL_TIMEOUT = 100;
  private static final int MAX_TIMEOUT = 2 * 60 * 1000; //2 min

  SocketIOConnectionManager()
  {
    rtLookupService = new RTLookupService( new Result<ReconnectAttempt>()
    {
      @Override
      public void handle( ReconnectAttempt result )
      {
        retryAttempt = result.getAttempt();
        reconnectAttempt( retryAttempt, result.getTimeout() );
        connectError( result.getError() );
      }
    } );
  }

  private final Object lock = new Object();
  private final RTLookupService rtLookupService;

  private int retryConnectTimeout = INITIAL_TIMEOUT;
  private int retryAttempt = 0;
  private Socket socket;

  Socket get()
  {
    if( socket != null || isConnected() )
    {
      logger.info( "Socket is connected" );
      return socket;
    }

    logger.info( "Socket not connected. Try to get lock" );

    synchronized( lock )
    {
      logger.info( "Got lock" );

      if( socket != null || isConnected() )
      {
        logger.info( "Socket is connected" );
        return socket;
      }

      final IO.Options opts = new IO.Options();
      opts.reconnection = false;

      opts.path = "/" + Backendless.getApplicationId();

      opts.query = "apiKey=" + Backendless.getSecretKey() + "&binary=true";

      final String host = rtLookupService.lookup( retryAttempt ) + opts.path;
      logger.info( "Looked up for server " + host );

      String userToken = HeadersManager.getInstance().getHeader( HeadersManager.HeadersEnum.USER_TOKEN_KEY );
      if( userToken != null && !userToken.isEmpty() )
        opts.query += "&userToken=" + userToken;

      try
      {
        socket = IO.socket( host, opts );
        logger.info( "Socket object created" );
      }
      catch( RuntimeException | URISyntaxException e )
      {
        connectError( e.getMessage() );
        logger.severe( e.getMessage() );
        return get();
      }

      socket.on( Socket.EVENT_CONNECT, new Emitter.Listener()
      {
        @Override
        public void call( Object... args )
        {
          logger.info( "Connected event" );
          retryConnectTimeout = INITIAL_TIMEOUT;
          retryAttempt = 0;
          connected();
        }
      } ).on( Socket.EVENT_DISCONNECT, new Emitter.Listener()
      {
        @Override
        public void call( Object... args )
        {
          final String error = Arrays.toString( args );
          logger.info( "Disconnected event " + error );
          disconnected( error );
          reconnect();
        }
      } ).on( Socket.EVENT_CONNECT_ERROR, new Emitter.Listener()
      {
        @Override
        public void call( Object... args )
        {
          final String error = Arrays.toString( args );
          logger.severe( "Connection failed " + error );
          connectError( error );
          reconnect();
        }
      } ).on( "SUB_RES", new Emitter.Listener()
      {
        @Override
        public void call( Object... args )
        {
          logger.info( "Got sub res" );
          subscriptionResult( args );
        }
      } ).on( "MET_RES", new Emitter.Listener()
      {
        @Override
        public void call( Object... args )
        {
          logger.info( "Got met res" );
          invocationResult( args );
        }
      } ).on( Socket.EVENT_ERROR, new Emitter.Listener()
      {
        @Override
        public void call( Object... args )
        {
          final String error = Arrays.toString( args );
          logger.severe( "ERROR from rt sever: " + error );
          connectError( error );
          reconnect();
        }
      } );

      socket.connect();
    }

    return socket;
  }

  private void reconnect()
  {
    if( socket == null )
      return;

    disconnect();
    logger.info( "Wait for " + retryConnectTimeout + " before reconnect" );
    try
    {
      Thread.sleep( retryConnectTimeout );
    }
    catch( InterruptedException e1 )
    {
      throw new RuntimeException( e1 );
    }

    retryConnectTimeout *= 2;

    if( retryConnectTimeout > MAX_TIMEOUT )
      retryConnectTimeout = MAX_TIMEOUT;

    retryAttempt++;

    reconnectAttempt( retryAttempt, retryConnectTimeout );

    get();
  }

  void disconnect()
  {
    logger.info( "Try to disconnect" );
    synchronized( lock )
    {
      if( socket != null )
        socket.close();

      socket = null;
    }
  }

  public boolean isConnected()
  {
    return socket != null && socket.connected();
  }

  abstract void connected();

  abstract void reconnectAttempt( int attempt, int timeout );

  abstract void connectError( String error );

  abstract void disconnected( String cause );

  abstract void subscriptionResult( Object... args );

  abstract void invocationResult( Object... args );
}
