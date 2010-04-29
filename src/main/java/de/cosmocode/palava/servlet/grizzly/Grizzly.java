/**
 * Copyright 2010 CosmoCode GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.cosmocode.palava.servlet.grizzly;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.glassfish.grizzly.web.Management;
import org.glassfish.grizzly.web.embed.GrizzlyWebServer;
import org.glassfish.grizzly.web.servlet.ServletAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.internal.Sets;
import com.google.inject.name.Named;

import de.cosmocode.palava.core.lifecycle.AutoStartable;
import de.cosmocode.palava.core.lifecycle.Initializable;
import de.cosmocode.palava.core.lifecycle.LifecycleException;
import de.cosmocode.palava.servlet.Webapp;

/**
 * A service which configures and manages an embedded grizzly servlet container.
 *
 * @author Willi Schoenborn
 */
final class Grizzly implements Initializable, AutoStartable {

    private static final Logger LOG = LoggerFactory.getLogger(Grizzly.class);
    
    private int port = 8080;
    
    private File webResourcesPath;
    
    private boolean secure;

    private Set<Webapp> webapps = Sets.newLinkedHashSet();
    
    private MBeanServer beanServer;
    
    private GrizzlyWebServer grizzly;

    @Inject(optional = true)
    void setPort(@Named(GrizzlyConfig.PORT) int port) {
        this.port = port;
    }
    
    @Inject(optional = true)
    void setWebResourcesPath(@Named(GrizzlyConfig.WEB_RESOURCES_PATH) File webResourcesPath) {
        this.webResourcesPath = Preconditions.checkNotNull(webResourcesPath, "WebResourcesPath");
    }
    
    @Inject(optional = true)
    void setSecure(@Named(GrizzlyConfig.SECURE) boolean secure) {
        this.secure = secure;
    }
    
    @Inject(optional = true)
    void setWebapps(Set<Webapp> webapps) {
        this.webapps = Preconditions.checkNotNull(webapps, "Webapps");
    }
    
    @Inject(optional = true)
    void setBeanServer(MBeanServer beanServer) {
        this.beanServer = Preconditions.checkNotNull(beanServer, "BeanServer");
    }
    
    @Override
    public void initialize() throws LifecycleException {
        LOG.info("Configuring grizzly to server resources from {} on {}", webResourcesPath, port);
        LOG.info("Processing secure connections : {}", Boolean.valueOf(secure));
        this.grizzly = new GrizzlyWebServer(port, webResourcesPath.getAbsolutePath(), secure);
        
        for (Webapp webapp : webapps) {
            LOG.info("Configuring webapp {}", webapp);
            final ServletAdapter adapter = new ServletAdapter();
            adapter.setRootFolder(webapp.getLocation());
            
            grizzly.addGrizzlyAdapter(adapter, new String[] {
                webapp.getContext()
            });
        }
        
        if (beanServer == null) {
            LOG.info("Running grizzly without jmx support");
        } else {
            LOG.info("Enabling jmx support for grizzly using {}", beanServer);
            grizzly.enableJMX(new Management() {
                
                @Override
                public void registerComponent(Object bean, ObjectName oname, String type) throws Exception {
                    beanServer.registerMBean(bean, oname);
                }
                
                @Override
                public void unregisterComponent(ObjectName oname) throws Exception {
                    beanServer.unregisterMBean(oname);
                }
                
            });
        }
    }
    
    @Override
    public void start() throws LifecycleException {
        try {
            grizzly.start();
        } catch (IOException e) {
            throw new LifecycleException(e);
        }
    }
    
    @Override
    public void stop() throws LifecycleException {
        grizzly.stop();
    };
    
}
