/**
 *
 * SIROCCO
 * Copyright (C) 2011 France Telecom
 * Contact: sirocco@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  $Id$
 *
 */

package org.ow2.sirocco.cloudmanager.core.impl;

import java.io.Serializable;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBContext;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.naming.InitialContext;

import org.apache.log4j.Logger;
import org.ow2.sirocco.cloudmanager.core.api.IJobManager;
import org.ow2.sirocco.cloudmanager.core.api.ILockManager;
import org.ow2.sirocco.cloudmanager.core.api.exception.CloudProviderException;
import org.ow2.sirocco.cloudmanager.core.utils.UtilsForManagers;
import org.ow2.sirocco.cloudmanager.model.cimi.Job;

@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destination", propertyValue = "JobCompletion")})
public class JobCompletionHandlerBean implements MessageListener {
    private static Logger logger = Logger.getLogger(JobCompletionHandlerBean.class.getName());

    private static final String JMS_TOPIC_CONNECTION_FACTORY_NAME = "JTCF";

    private static final String JMS_TOPIC_NAME = "JobCompletion";

    private static final long JMS_REDELIVERY_DELAY = 1 * 3000;

    @EJB
    private IJobManager jobManager;

    @EJB
    private ILockManager lockManager;

    @Resource
    private EJBContext ctx;

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onMessage(final Message msg) {
        if (msg instanceof ObjectMessage) {
            ObjectMessage objectMessage = (ObjectMessage) msg;
            Long counter = new Long(0);
            try {
                counter = objectMessage.getLongProperty("deliveriesCounter");
            } catch (JMSException e1) {
                JobCompletionHandlerBean.logger.warn("Failed to extract deliveriesCounter from JMS message", e1);
            } catch (NumberFormatException e1) {
                counter = new Long(0);
            }
            if (counter == null) {
                counter = new Long(0);
            }
            Object payload;
            try {
                payload = objectMessage.getObject();
                JobCompletionHandlerBean.logger.debug("On topic JobCompletion: received " + payload);
            } catch (JMSException ex) {
                JobCompletionHandlerBean.logger.error("Failed to extract from JMS message", ex);
                return;
            }
            Job providerJob = (Job) payload;
            String jobId = providerJob.getProviderAssignedId();
            // we call jobManager to deal with events
            String topmostid = "";
            boolean locked = false;
            try {
                topmostid = this.jobManager.getTopmostJobId(this.jobManager.getJobIdFromProvider(providerJob));
                this.lockManager.lock(topmostid, Job.class.getCanonicalName());
                locked = true;
                this.jobManager.handleWorkflowEvent(providerJob);

            } catch (Exception e) {
                // this.ctx.setRollbackOnly();
                JobCompletionHandlerBean.logger.warn("JobCompletion message rollbacked " + counter + " times - " + jobId);
                // + e.getMessage(), e);

                // reemiting message with delay, and counter updated
                if (counter < 50) {
                    counter += 1;
                    try {
                        UtilsForManagers.emitJobCompletionMessage(((ObjectMessage) msg).getObject(), this.ctx,
                            Math.round(1000 * Math.pow(2., counter)), counter);
                    } catch (JMSException e2) {
                        JobCompletionHandlerBean.logger.warn("JobCompletion message resend exception - " + jobId);
                    } catch (Exception e2) {
                        JobCompletionHandlerBean.logger.warn("JobCompletion message resend exception - " + jobId);
                    }
                } else {
                    JobCompletionHandlerBean.logger.error("JobCompletion message dropped (too many times rollbacked)");
                }

                try {
                    if (locked) {
                        this.lockManager.unlockUntransacted(topmostid, Job.class.getCanonicalName());
                    }
                } catch (CloudProviderException ee) {
                    // TODO Auto-generated catch block
                    ee.printStackTrace();
                }

                /*try {
                    // not possible to set a redelevery time in Joram/Jonas
                    Thread.sleep(JobCompletionHandlerBean.JMS_REDELIVERY_DELAY
                        + (long) Math.floor(Math.random() * JobCompletionHandlerBean.JMS_REDELIVERY_DELAY));
                } catch (InterruptedException e1) {
                    JobCompletionHandlerBean.logger.warn("InterruptedException! - " + jobId);
                    // e1.printStackTrace();
                }*/
            }
        }
    }

    @SuppressWarnings("unused")
    private void emitMessage(final Serializable payload) throws Exception {
        InitialContext ctx = new InitialContext();
        TopicConnectionFactory topicConnectionFactory = (TopicConnectionFactory) ctx
            .lookup(JobCompletionHandlerBean.JMS_TOPIC_CONNECTION_FACTORY_NAME);
        TopicConnection connection = topicConnectionFactory.createTopicConnection();
        TopicSession session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic cloudAdminTopic = (Topic) ctx.lookup(JobCompletionHandlerBean.JMS_TOPIC_NAME);
        TopicPublisher topicPublisher = session.createPublisher(cloudAdminTopic);
        ObjectMessage message = session.createObjectMessage();
        message.setObject(payload);
        topicPublisher.publish(message);
        JobCompletionHandlerBean.logger.info("EMITTED EVENT " + payload.toString() + " on "
            + JobCompletionHandlerBean.JMS_TOPIC_NAME + " topic");
        topicPublisher.close();
        session.close();
        connection.close();
    }

}