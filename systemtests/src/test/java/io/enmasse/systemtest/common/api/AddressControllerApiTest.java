/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.common.api;

import io.enmasse.systemtest.*;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.resources.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IsolatedAddressSpace.class)
public class AddressControllerApiTest extends TestBase {
    private static Logger log = CustomLogger.getLogger();

    @Override
    protected String getDefaultPlan(AddressType addressType) {
        return null;
    }

    @Before
    public void setUp() {
        plansProvider.setUp();
    }

    @After
    public void tearDown() {
        plansProvider.tearDown();
    }

    @Test
    public void testRestApiGetSchema() throws Exception {
        AddressPlan queuePlan = new AddressPlan("test-schema-rest-api-addr-plan", AddressType.QUEUE,
                Collections.singletonList(new AddressResource("broker", 0.6)));
        plansProvider.createAddressPlanConfig(queuePlan);

        //define and create address space plan
        List<AddressSpaceResource> resources = Arrays.asList(
                new AddressSpaceResource("broker", 0.0, 2.0),
                new AddressSpaceResource("router", 1.0, 1.0),
                new AddressSpaceResource("aggregate", 0.0, 2.0));
        List<AddressPlan> addressPlans = Collections.singletonList(queuePlan);
        AddressSpacePlan addressSpacePlan = new AddressSpacePlan("schema-rest-api-plan", "schema-rest-api-plan",
                "standard-space", AddressSpaceType.STANDARD, resources, addressPlans);
        plansProvider.createAddressSpacePlanConfig(addressSpacePlan);

        Future<SchemaData> data = getSchema();
        SchemaData schemaData = data.get(20, TimeUnit.SECONDS);
        log.info("Check if schema object is not null");
        assertThat(schemaData.getAddressSpaceTypes().size(), not(0));

        log.info("Check if schema object contains new address space plan");
        assertTrue(schemaData.getAddressSpaceType("standard").getPlans()
                .stream()
                .map(PlanData::getName)
                .collect(Collectors.toList()).contains("schema-rest-api-plan"));

        log.info("Check if schema contains new address plans");
        assertTrue(schemaData.getAddressSpaceType("standard").getAddressType("queue").getPlans().stream()
                .filter(s -> s.getName().equals("test-schema-rest-api-addr-plan"))
                .map(PlanData::getName)
                .collect(Collectors.toList())
                .contains("test-schema-rest-api-addr-plan"));
    }

    @Test
    public void testRestApiAddressResourceOptionalParams() throws Exception {
        AddressSpace addressSpace = new AddressSpace("test-rest-api-addr-space", AddressSpaceType.BROKERED);
        createAddressSpace(addressSpace, AuthService.NONE.toString());

        AddressSpace addressSpace2 = new AddressSpace("test-rest-api-addr-space2", AddressSpaceType.BROKERED);
        createAddressSpace(addressSpace2, AuthService.NONE.toString());

        logWithSeparator(log, "Check if uuid is propagated");
        String uuid = "4bfe49c2-60b5-11e7-a5d0-507b9def37d9";
        Destination dest1 = new Destination("test-rest-api-queue", uuid, addressSpace.getName(),
                "test-rest-api-queue", AddressType.QUEUE.toString(), "brokered-queue");

        setAddresses(addressSpace, dest1);
        Address dest1AddressObj = getAddressesObjects(addressSpace, Optional.of(dest1.getAddress())).get(20, TimeUnit.SECONDS).get(0);
        assertEquals("Address uuid is not equal", uuid, dest1AddressObj.getUuid());

        logWithSeparator(log, "Check if name is optional");
        Destination dest2 = new Destination(null, null, addressSpace.getName(),
                "test-rest-api-queue2", AddressType.QUEUE.toString(), "brokered-queue");
        setAddresses(addressSpace, dest2);

        Address dest2AddressObj = getAddressesObjects(addressSpace, Optional.empty()).get(20, TimeUnit.SECONDS).get(0);
        assertEquals("Address name is empty",
                String.format("%s-%s", dest2AddressObj.getAddress(), dest2AddressObj.getUuid()), dest2AddressObj.getName());

        logWithSeparator(log, "Check if adddressSpace is optional");
        Destination dest3 = new Destination(null, null, null,
                "test-rest-api-queue3", AddressType.QUEUE.toString(), "brokered-queue");
        setAddresses(addressSpace, dest3);

        Address dest3AddressObj = getAddressesObjects(addressSpace, Optional.empty()).get(20, TimeUnit.SECONDS).get(0);
        assertEquals("Addressspace name is empty",
                addressSpace.getName(), dest3AddressObj.getAddressSpace());

        logWithSeparator(log, "Check if behavior when addressSpace is set to another existing address space");
        Destination dest4 = new Destination(null, null, addressSpace2.getName(),
                "test-rest-api-queue4", AddressType.QUEUE.toString(), "brokered-queue");
        try {
            setAddresses(addressSpace, dest4);
        } catch (java.util.concurrent.ExecutionException ex) {
            assertTrue("Exception does not contain right information",
                    ex.getMessage().contains("does not match address space in url"));
        }
    }
}