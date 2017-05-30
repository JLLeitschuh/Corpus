/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.executor;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.executor.tasks.*;
import com.hazelcast.core.*;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ProblematicTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.hazelcast.test.HazelcastTestSupport.*;
import static org.junit.Assert.*;

@RunWith(HazelcastParallelClassRunner.class)
@Category(QuickTest.class)
public class ClientExecutorServiceInvokeTest {

    static HazelcastInstance instance1;
    static HazelcastInstance client;

    @BeforeClass
    public static void init() {
        instance1 = Hazelcast.newHazelcastInstance();
        client = HazelcastClient.newHazelcastClient();
    }

    @AfterClass
    public static void destroy() {
        HazelcastClient.shutdownAll();
        Hazelcast.shutdownAll();
    }

    @Test
    public void testInvokeAll() throws Throwable {
        IExecutorService service = client.getExecutorService(randomString());
        String msg = randomString();
        Collection c = new ArrayList();
        c.add(new AppendCallable(msg));
        c.add(new AppendCallable(msg));

        List<Future> results =  service.invokeAll(c);
        for(Future result : results){
            assertEquals(msg + AppendCallable.APPENDAGE,  result.get() );
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvokeAll_withTimeOut() throws Throwable {
        IExecutorService service = client.getExecutorService(randomString());
        Collection c = new ArrayList();
        c.add(new AppendCallable());
        c.add(new AppendCallable());
        service.invokeAll(c, 1, TimeUnit.MINUTES);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvokeAny() throws Throwable, InterruptedException {
        IExecutorService service = client.getExecutorService(randomString());
        Collection c = new ArrayList();
        c.add(new AppendCallable());
        c.add(new AppendCallable());
        service.invokeAny(c);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvokeAnyTimeOut() throws Throwable, InterruptedException {
        IExecutorService service = client.getExecutorService(randomString());
        Collection c = new ArrayList();
        c.add(new AppendCallable());
        c.add(new AppendCallable());
        service.invokeAny(c, 1, TimeUnit.MINUTES);
    }

}