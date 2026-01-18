/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.transaction.manager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.micro.core.Constants;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

//TODO: need a way to solve javax.transaction service dependency
@Component(name = "org.wso2.micro.integrator.transaction.manager.TransactionManagerComponent",
        immediate = true)
public class TransactionManagerComponent {

    private static Log log = LogFactory.getLog(TransactionManagerComponent.class);

    private static TransactionManager txManager;

    private static UserTransaction userTransaction;

    /* class level lock for controlling synchronized access to static variables */
    private static Object txManagerComponentLock = new Object();

    @Activate
    protected void activate(ComponentContext ctxt) {

        bindTransactionManagerWithJNDI();
        log.debug("Transaction Manager bundle is activated ");
    }

    @Deactivate
    protected void deactivate(ComponentContext ctxt) {
        log.debug("Transaction Manager bundle is deactivated ");
    }

    @Reference(name = "transactionmanager",
            service = TransactionManager.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetTransactionManager")
    protected void setTransactionManager(TransactionManager txManager) {

        synchronized (txManagerComponentLock) {

            log.debug("Setting the Transaction Manager Service");
            TransactionManagerComponent.txManager = txManager;
        }
    }

    protected void unsetTransactionManager(TransactionManager txManager) {

        synchronized (txManagerComponentLock) {

            log.debug("Unsetting the Transaction Manager Service");
            TransactionManagerComponent.txManager = null;
        }
    }

    private static TransactionManager getTransactionManager() {
        return txManager;
    }

    @Reference(name = "usertransaction",
            service = UserTransaction.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetUserTransaction")
    protected void setUserTransaction(UserTransaction userTransaction) {

        synchronized (txManagerComponentLock) {

            log.debug("Setting the UserTransaction Service");
            TransactionManagerComponent.userTransaction = userTransaction;
        }
    }

    protected void unsetUserTransaction(UserTransaction userTransaction) {

        synchronized (txManagerComponentLock) {

            log.debug("Unsetting the UserTransaction Service");
            TransactionManagerComponent.userTransaction = null;
        }
    }

    private static UserTransaction getUserTransaction() {
        return userTransaction;
    }

    private static void bindTransactionManagerWithJNDI() {

        try {

            String defaultInitialContextFactory = MicroIntegratorBaseUtils.getServerConfiguration()
                    .getFirstProperty("JNDI.DefaultInitialContextFactory");

            System.setProperty(Context.INITIAL_CONTEXT_FACTORY, defaultInitialContextFactory);

            System.setProperty(Context.URL_PKG_PREFIXES, "org.wso2.micro.tomcat.jndi");

            Context currentCtx = new InitialContext();

            try {
                currentCtx.lookup("java:comp");
            } catch (NameNotFoundException ignore) {
                currentCtx = currentCtx.createSubcontext("java:comp");
            }

            try {
                currentCtx.lookup("java:comp/TransactionManager");
            } catch (NameNotFoundException ignore) {
                currentCtx.bind("TransactionManager", getTransactionManager());
            }

            try {
                currentCtx.lookup("java:comp/UserTransaction");
            } catch (NameNotFoundException ignore) {
                currentCtx.bind("UserTransaction", getUserTransaction());
            }

        } catch (Exception e) {
            log.error("Error in binding transaction manager for tenant: " + Constants.SUPER_TENANT_ID, e);
        }
    }
}
