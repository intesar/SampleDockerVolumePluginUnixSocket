/**
 * COPYRIGHT (C) 2016 HyperGrid. All Rights Reserved.
 * <p>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dchq.docker.volume.driver.main;

import com.dchq.docker.volume.driver.controller.CustomConverter;
import com.dchq.docker.volume.driver.controller.SocketController;
import com.dchq.docker.volume.driver.service.DockerVolumeDriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author Intesar Mohammed
 */
@SpringBootApplication(scanBasePackages = {"com.dchq"})
public class Application {

    final Logger logger = LoggerFactory.getLogger(getClass());

    public Application() {
        logger.info("DCHQ Volume Driver...initialized!");
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public SocketController socket(@Value("${docker.socket}") String SOCKET_PATH, CustomConverter converter, DockerVolumeDriverService service) {
        SocketController socket = new SocketController();
        socket.loadSocketListener(SOCKET_PATH, service, converter);
        return socket;
    }
}