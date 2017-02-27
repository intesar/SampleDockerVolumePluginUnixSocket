package com.dchq.docker.volume.driver.controller;

import com.dchq.docker.volume.driver.dto.Base;
import com.dchq.docker.volume.driver.dto.BaseResponse;
import com.dchq.docker.volume.driver.service.DockerVolumeDriverService;
import jnr.enxio.channels.NativeSelectorProvider;
import jnr.unixsocket.UnixServerSocket;
import jnr.unixsocket.UnixServerSocketChannel;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO: Implement Jnr-socket
 */
//@Component
public class SocketController {

    final org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());

    static DockerVolumeDriverService service;
    static CustomConverter converter;

    java.io.File path = null;

    //String SOCKET_PATH = "/run/docker/plugins/hypercloud.sock";

    public void loadSocketListener(final String SOCKET_PATH, DockerVolumeDriverService service, CustomConverter converter) {

        this.service = service;
        this.converter = converter;

        try {
            logger.info("Registering socket [{}]", SOCKET_PATH);
            path = new java.io.File(SOCKET_PATH);
            //FileUtils.forceMkdirParent(path);
            path.deleteOnExit();
            UnixSocketAddress address = new UnixSocketAddress(path);
            UnixServerSocketChannel channel = UnixServerSocketChannel.open();

            try {
                Selector sel = NativeSelectorProvider.getInstance().openSelector();
                channel.configureBlocking(false);
                channel.socket().bind(address);
                logger.debug("channel.register begin");
                channel.register(sel, SelectionKey.OP_ACCEPT, new ServerActor(channel, sel));
                logger.debug("channel.register end");
                while (sel.select() >= 0) {
                    logger.debug("Selector > 0");
                    Set<SelectionKey> keys = sel.selectedKeys();
                    Iterator<SelectionKey> iterator = keys.iterator();
                    boolean running = false;
                    boolean cancelled = false;
                    while (iterator.hasNext()) {
                        logger.debug("SelectionKey.hasNext");
                        SelectionKey k = iterator.next();
                        Actor a = (Actor) k.attachment();
                        if (a.rxready(path)) {
                            running = true;
                        } else {
                            k.cancel();
                            cancelled = true;
                        }
                        iterator.remove();
                    }
                    if (!running && cancelled) {
                        logger.info("No Actors Running any more");
                        channel.register(sel, SelectionKey.OP_ACCEPT, new ServerActor(channel, sel));
                        //break;
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(UnixServerSocket.class.getName()).log(Level.SEVERE, null, ex);
            }
            logger.info("UnixServer EXIT");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileUtils.deleteQuietly(path);
        }
    }

    static interface Actor {
        public boolean rxready(java.io.File path);
    }

    static final class ServerActor implements Actor {
        final org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());
        private final UnixServerSocketChannel channel;
        private final Selector selector;

        public ServerActor(UnixServerSocketChannel channel, Selector selector) {
            this.channel = channel;
            this.selector = selector;
            logger.debug("ServerActor instantiated!");
        }

        public final boolean rxready(java.io.File path) {
            try {
                UnixSocketChannel client = channel.accept();
                client.configureBlocking(false);
                client.register(selector, SelectionKey.OP_READ, new ClientActor(client));
                logger.debug("ServerActor ready!");
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    static final class ClientActor implements Actor {
        final org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());

        String HTTP_RESPONSE = "HTTP/1.1 200 OK\r\n" + "Content-Type: application/vnd.docker.plugins.v1.2+json\r\n\r\n";

        private final UnixSocketChannel channel;

        public ClientActor(UnixSocketChannel channel) {
            this.channel = channel;
            logger.debug("ClientActor instantiated!");
        }

        public final boolean rxready(java.io.File path) {
            try {
                logger.debug("ClientActor ready!");
                ByteBuffer buf = ByteBuffer.allocate(1024);
                int n = channel.read(buf);
                UnixSocketAddress remote = channel.getRemoteSocketAddress();

                if (n > 0) {
                    // System.out.printf("Read in %d bytes from %s\n", n, remote);
                    //buf.flip();
                    //channel.write(buf);
                    String req = new String(buf.array(), 0, buf.position());
                    //System.out.print("Data From Client :" + req + "\n");
                    buf.flip();
                    Base response = null;
                    RequestWrapper request = HttpRequestParser.parse(req);

                    response = getBaseResponse(request);
                    String responseText = HTTP_RESPONSE + converter.convertFromBaseResponse(response);

                    logger.info("Response text [{}]", responseText);

                    //buf.flip();
                    ByteBuffer bb = ByteBuffer.wrap(responseText.getBytes(Charset.defaultCharset()));
                    logger.debug("bb [{}]", bb.toString());
                    channel.write(bb);

                    //channel.finishConnect();
                    channel.close();


                    return false;
                } else if (n < 0) {
                    return false;
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
            return true;
        }

        private Base getBaseResponse(RequestWrapper request) {

            String requestType = request.getPath();
            Base response = new Base();

            switch (requestType) {
                case "/Plugin.Activate":
                    response = service.activate();
                    break;
                case "/VolumeDriver.Capabilities":
                    response = service.capabilities();
                    break;
                case "/VolumeDriver.Create":
                    response = service.create(converter.convertToCreateRequest(request.getBody()));
                    break;
                case "/VolumeDriver.Mount":
                    response = service.mount(converter.convertToMountRequest(request.getBody()));
                    break;
                case "/VolumeDriver.Unmount":
                    response = service.unmount(converter.convertToMountRequest(request.getBody()));
                    break;
                case "/VolumeDriver.Get":
                    response = service.get(converter.convertToGetRequest(request.getBody()));
                    break;
                case "/VolumeDriver.List":
                    response = service.list();
                    break;
                case "/VolumeDriver.Path":
                    response = service.path(converter.convertToPathRequest(request.getBody()));
                    break;
                case "/VolumeDriver.Remove":
                    response = service.remove(converter.convertToRemoveRequest(request.getBody()));
                    break;
            }

            if (response == null) {

                response = new BaseResponse();
                //response.setErr("Invalid Request");

            }

            return response;
        }
    }

}
