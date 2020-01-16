/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.bases.web;


import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressBuilder;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.systemtest.UserCredentials;
import io.enmasse.systemtest.amqp.AmqpClient;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.bases.shared.ITestBaseShared;
import io.enmasse.systemtest.logs.CustomLogger;
import io.enmasse.systemtest.messagingclients.ExternalMessagingClient;
import io.enmasse.systemtest.messagingclients.rhea.RheaClientConnector;
import io.enmasse.systemtest.model.address.AddressStatus;
import io.enmasse.systemtest.model.address.AddressType;
import io.enmasse.systemtest.model.addressplan.DestinationPlan;
import io.enmasse.systemtest.platform.Kubernetes;
import io.enmasse.systemtest.selenium.SeleniumProvider;
import io.enmasse.systemtest.selenium.page.AddressSpaceConsoleWebPage;
import io.enmasse.systemtest.selenium.resources.AddressWebItem;
import io.enmasse.systemtest.selenium.resources.ConnectionWebItem;
import io.enmasse.systemtest.selenium.resources.FilterType;
import io.enmasse.systemtest.selenium.resources.SortType;
import io.enmasse.systemtest.time.TimeoutBudget;
import io.enmasse.systemtest.utils.AddressSpaceUtils;
import io.enmasse.systemtest.utils.AddressUtils;
import io.enmasse.systemtest.utils.TestUtils;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class WebConsoleTest extends TestBase implements ITestBaseShared {
    private static Logger log = CustomLogger.getLogger();
    SeleniumProvider selenium = SeleniumProvider.getInstance();
    private List<ExternalMessagingClient> clientsList;


    private AddressSpaceConsoleWebPage addressSpaceConsoleWebPage;

    @AfterEach
    public void tearDownWebConsoleTests() {
        if (clientsList != null) {
            getClientUtils().stopClients(clientsList);
            clientsList.clear();
        }
    }

    //============================================================================================
    //============================ do test methods ===============================================
    //============================================================================================

    protected void doTestCreateDeleteAddress(Address... destinations) throws Exception {
        Kubernetes.getInstance().getAddressClient().inNamespace(getSharedAddressSpace().getMetadata().
                getNamespace()).list().getItems().forEach(address -> log.warn("Add from list: " + address));
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        for (Address dest : destinations) {
            addressSpaceConsoleWebPage.createAddressWebConsole(dest, true);
            addressSpaceConsoleWebPage.deleteAddressWebConsole(dest);
        }
        assertWaitForValue(0, () -> addressSpaceConsoleWebPage.getResultsCount(), new TimeoutBudget(20, TimeUnit.SECONDS));
    }

    protected void doTestCreateDeleteDurableSubscription(Address... destinations) throws Exception {
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.openAddressesPageWebConsole();


        for (Address dest : destinations) {
            //create topic
            addressSpaceConsoleWebPage.createAddressWebConsole(dest);
            AddressUtils.waitForDestinationsReady(new TimeoutBudget(5, TimeUnit.MINUTES), dest);
            log.info("Address topic: " + dest);
            //create subscription
            Address subscription = new AddressBuilder()
                    .withNewMetadata()
                    .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                    .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), dest.getSpec().getAddress() + "-subscriber"))
                    .endMetadata()
                    .withNewSpec()
                    .withType("subscription")
                    .withAddress(dest.getSpec().getAddress() + "-subscriber")
                    .withTopic(dest.getSpec().getAddress())
                    .withPlan(DestinationPlan.STANDARD_LARGE_SUBSCRIPTION)
                    .endSpec()
                    .build();
            addressSpaceConsoleWebPage.createAddressWebConsole(subscription);
            AddressUtils.waitForDestinationsReady(new TimeoutBudget(5, TimeUnit.MINUTES), subscription);
            log.info("Subscription add: " + subscription);

            Kubernetes.getInstance().getAddressClient().inNamespace(getSharedAddressSpace().getMetadata().
                    getNamespace()).list().getItems().forEach(address -> log.warn("Add from list: " + address));

            assertWaitForValue(2, () -> addressSpaceConsoleWebPage.getResultsCount(), new TimeoutBudget(120, TimeUnit.SECONDS));

            //delete topic and sub
            addressSpaceConsoleWebPage.deleteAddressWebConsole(subscription);
            addressSpaceConsoleWebPage.deleteAddressWebConsole(dest);
        }
        assertWaitForValue(0, () -> addressSpaceConsoleWebPage.getResultsCount(), new TimeoutBudget(20, TimeUnit.SECONDS));
    }

    protected void doTestAddressStatus(Address destination) throws Exception {
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressWebConsole(destination, false);
        assertThat("Console failed, expected PENDING or READY state",
                addressSpaceConsoleWebPage.getAddressItem(destination).getStatus(),
                either(is(AddressStatus.PENDING)).or(is(AddressStatus.READY)));

        AddressUtils.waitForDestinationsReady(new TimeoutBudget(5, TimeUnit.MINUTES), destination);

        assertEquals(AddressStatus.READY, addressSpaceConsoleWebPage.getAddressItem(destination).getStatus(),
                "Console failed, expected READY state");
    }

    protected void doTestFilterAddressesByType() throws Exception {
        int addressCount = 4;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));
        assertThat(String.format("Console failed, does not contain %d addresses", addressCount),
                addressSpaceConsoleWebPage.getAddressItems().size(), is(addressCount));

        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.TYPE, AddressType.QUEUE.toString());
        List<AddressWebItem> items = addressSpaceConsoleWebPage.getAddressItems();
        assertThat(String.format("Console failed, does not contain %d addresses", addressCount / 2),
                items.size(), is(addressCount / 2)); //assert correct count
        assertAddressType("Console failed, does not contains only address type queue",
                items, AddressType.QUEUE); //assert correct type

        addressSpaceConsoleWebPage.removeFilterByType(AddressType.QUEUE.toString());
        assertThat(String.format("Console failed, does not contain %d addresses", addressCount),
                addressSpaceConsoleWebPage.getAddressItems().size(), is(addressCount));

        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.TYPE, AddressType.TOPIC.toString());
        items = addressSpaceConsoleWebPage.getAddressItems();
        assertThat(String.format("Console failed, does not contain %d addresses", addressCount / 2),
                items.size(), is(addressCount / 2)); //assert correct count
        assertAddressType("Console failed, does not contains only address type topic",
                items, AddressType.TOPIC); //assert correct type

        addressSpaceConsoleWebPage.removeFilterByType(AddressType.TOPIC.toString());
        assertThat(String.format("Console failed, does not contain %d addresses", addressCount),
                addressSpaceConsoleWebPage.getAddressItems().size(), is(addressCount));
    }

    protected void doTestFilterAddressesByName() throws Exception {
        int addressCount = 4;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));

        String subText = "web";
        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, subText);
        List<AddressWebItem> items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(addressCount, items.size(),
                String.format("Console failed, does not contain %d addresses", addressCount));
        assertAddressName("Console failed, does not contain addresses contain " + subText, items, subText);

        subText = "via";
        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, subText);
        items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(addressCount, items.size(),
                String.format("Console failed, does not contain %d addresses", addressCount));
        assertAddressName("Console failed, does not contain addresses contain " + subText, items, subText);

        subText = "web";
        addressSpaceConsoleWebPage.removeFilterByName(subText);
        items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(addressCount, items.size(),
                String.format("Console failed, does not contain %d addresses", addressCount));
        assertAddressName("Console failed, does not contain addresses contain " + subText, items, subText);

        subText = "queue";
        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, subText);
        items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(addressCount / 2, items.size(),
                String.format("Console failed, does not contain %d addresses", addressCount / 2));
        assertAddressName("Console failed, does not contain addresses contain " + subText, items, subText);

        addressSpaceConsoleWebPage.clearAllFilters();
        assertEquals(addressCount, addressSpaceConsoleWebPage.getAddressItems().size(),
                String.format("Console failed, does not contain %d addresses", addressCount));
    }

    protected void doTestDeleteFilteredAddress() throws Exception {
        String testString = "addressName";
        List<AddressWebItem> items;
        int addressTotal = 2;

        Address destQueue = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), testString + "queue"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress(testString + "queue")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();

        Address destTopic = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), testString + "topic"))
                .endMetadata()
                .withNewSpec()
                .withType("topic")
                .withAddress(testString + "topic")
                .withPlan(getDefaultPlan(AddressType.TOPIC))
                .endSpec()
                .build();

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressWebConsole(destQueue);
        addressSpaceConsoleWebPage.createAddressWebConsole(destTopic);

        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, "queue");
        items = addressSpaceConsoleWebPage.getAddressItems();

        assertEquals(addressTotal / 2, items.size(),
                String.format("Console failed, filter does not contain %d addresses", addressTotal / 2));

        assertAddressName("Console failed, filter does not contain addresses", items, "queue");

        addressSpaceConsoleWebPage.deleteAddressWebConsole(destQueue);
        items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(0, items.size());
        log.info("filtered address has been deleted and no longer present in filter");

        addressSpaceConsoleWebPage.clearAllFilters();
        items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(addressTotal / 2, items.size());
    }

    protected void doTestFilterAddressWithRegexSymbols() throws Exception {
        int addressCount = 4;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));
        assertThat(String.format("Console failed, does not contain %d addresses", addressCount),
                addressSpaceConsoleWebPage.getAddressItems().size(), is(addressCount));

        //valid filter, will show 2 results
        String subText = "topic";
        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, subText);
        List<AddressWebItem> items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(addressCount / 2, items.size(),
                String.format("Console failed, does not contain %d addresses", addressCount / 2));
        assertAddressName("Console failed, does not contain addresses contain " + subText, items, subText);
        addressSpaceConsoleWebPage.clearAllFilters();

        //invalid filter (not regex), error message is shown
        subText = "*";
        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, subText);
        WebElement regexAlert = selenium.getWebElement(() -> selenium.getDriver().findElement(By.className("pficon-error-circle-o")));
        assertTrue(regexAlert.isDisplayed());

        //valid regex filter (.*), will show 4 results
        subText = ".*";
        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, subText);
        items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(addressCount, items.size(),
                String.format("Console failed, does not contain %d addresses", addressCount));
        addressSpaceConsoleWebPage.clearAllFilters();

        //valid regex filter ([0-9]\d*$) = any address ending with a number, will show 4 results
        subText = "[0-9]\\d*$";
        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, subText);
        items = addressSpaceConsoleWebPage.getAddressItems();
        assertEquals(addressCount, items.size(),
                String.format("Console failed, does not contain %d addresses", addressCount));
        addressSpaceConsoleWebPage.clearAllFilters();
    }

    protected void doTestRegexAlertBehavesConsistently() throws Exception {
        String subText = "*";
        int addressCount = 2;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));

        assertThat(String.format("Console failed, does not contain %d addresses", addressCount),
                addressSpaceConsoleWebPage.getAddressItems().size(), is(addressCount));

        addressSpaceConsoleWebPage.addAddressesFilter(FilterType.NAME, subText);
        WebElement regexAlert = addressSpaceConsoleWebPage.getFilterRegexAlert();
        assertTrue(regexAlert.isDisplayed());
        addressSpaceConsoleWebPage.clickOnRegexAlertClose();
        assertFalse(regexAlert.isDisplayed());

        //check on connections tab filter
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();
        addressSpaceConsoleWebPage.addConnectionsFilter(FilterType.HOSTNAME, subText);
        regexAlert = addressSpaceConsoleWebPage.getFilterRegexAlert();
        assertTrue(regexAlert.isDisplayed());
        addressSpaceConsoleWebPage.clickOnRegexAlertClose();
        assertFalse(regexAlert.isDisplayed());
    }

    protected void doTestSortAddressesByName() throws Exception {
        int addressCount = 4;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));

        addressSpaceConsoleWebPage.sortItems(SortType.NAME, true);
        assertSorted("Console failed, items are not sorted by name asc", addressSpaceConsoleWebPage.getAddressItems());

        addressSpaceConsoleWebPage.sortItems(SortType.NAME, false);
        assertSorted("Console failed, items are not sorted by name desc", addressSpaceConsoleWebPage.getAddressItems(), true);
    }

    protected void doTestSortAddressesByClients() throws Exception {
        int addressCount = 4;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));
        addressSpaceConsoleWebPage.openAddressesPageWebConsole();

        List<ExternalMessagingClient> receivers = getClientUtils().attachReceivers(getSharedAddressSpace(), addresses, -1, defaultCredentials);
        try {
            Thread.sleep(15000);

            addressSpaceConsoleWebPage.sortItems(SortType.RECEIVERS, true);
            assertSorted("Console failed, items are not sorted by count of receivers asc",
                    addressSpaceConsoleWebPage.getAddressItems(), Comparator.comparingInt(AddressWebItem::getReceiversCount));

            addressSpaceConsoleWebPage.sortItems(SortType.RECEIVERS, false);
            assertSorted("Console failed, items are not sorted by count of receivers desc",
                    addressSpaceConsoleWebPage.getAddressItems(), true, Comparator.comparingInt(AddressWebItem::getReceiversCount));
        } finally {
            getClientUtils().stopClients(receivers);
        }

        List<ExternalMessagingClient> senders = getClientUtils().attachSenders(getSharedAddressSpace(), addresses, 360, defaultCredentials);
        try {

            Thread.sleep(15000);
            addressSpaceConsoleWebPage.sortItems(SortType.SENDERS, true);
            assertSorted("Console failed, items are not sorted by count of senders asc",
                    addressSpaceConsoleWebPage.getAddressItems(), Comparator.comparingInt(AddressWebItem::getSendersCount));

            addressSpaceConsoleWebPage.sortItems(SortType.SENDERS, false);
            assertSorted("Console failed, items are not sorted by count of senders desc",
                    addressSpaceConsoleWebPage.getAddressItems(), true, Comparator.comparingInt(AddressWebItem::getSendersCount));
        } finally {
            getClientUtils().stopClients(senders);
        }

    }

    protected void doTestSortConnectionsBySenders() throws Exception {
        int addressCount = 2;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();

        assertEquals(0, addressSpaceConsoleWebPage.getConnectionItems().size(), "Unexpected number of connections present before attaching clients");

        clientsList = attachClients(addresses);

        boolean pass = false;
        try {
            addressSpaceConsoleWebPage.sortItems(SortType.SENDERS, true);
            assertSorted("Console failed, items are not sorted by count of senders asc",
                    addressSpaceConsoleWebPage.getConnectionItems(6), Comparator.comparingInt(ConnectionWebItem::getSendersCount));

            addressSpaceConsoleWebPage.sortItems(SortType.SENDERS, false);
            assertSorted("Console failed, items are not sorted by count of senders desc",
                    addressSpaceConsoleWebPage.getConnectionItems(6), true, Comparator.comparingInt(ConnectionWebItem::getSendersCount));
            pass = true;
        } finally {
            if (!pass) {
                clientsList.forEach(c -> {
                    c.stop();
                    log.info("=======================================");
                    log.info("stderr {}", c.getStdError());
                    log.info("stdout {}", c.getStdOutput());
                });
                clientsList.clear();
            }

        }
    }

    protected void doTestSortConnectionsByReceivers() throws Exception {
        int addressCount = 2;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();

        clientsList = attachClients(addresses);

        addressSpaceConsoleWebPage.sortItems(SortType.RECEIVERS, true);
        assertSorted("Console failed, items are not sorted by count of receivers asc",
                addressSpaceConsoleWebPage.getConnectionItems(6), Comparator.comparingInt(ConnectionWebItem::getReceiversCount));

        addressSpaceConsoleWebPage.sortItems(SortType.RECEIVERS, false);
        assertSorted("Console failed, items are not sorted by count of receivers desc",
                addressSpaceConsoleWebPage.getConnectionItems(6), true, Comparator.comparingInt(ConnectionWebItem::getReceiversCount));
    }


    protected void doTestFilterConnectionsByEncrypted() throws Exception {
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        Address queue = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue-connection-encrypted"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("queue-connection-encrypted")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        addressSpaceConsoleWebPage.createAddressesWebConsole(queue);
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();

        int receiverCount = 5;
        clientsList = getClientUtils().attachReceivers(getSharedAddressSpace(), queue, receiverCount, -1, defaultCredentials);

        addressSpaceConsoleWebPage.addConnectionsFilter(FilterType.ENCRYPTED, "encrypted");
        List<ConnectionWebItem> items = addressSpaceConsoleWebPage.getConnectionItems(receiverCount);
        assertThat(String.format("Console failed, does not contain %d connections", receiverCount),
                items.size(), is(receiverCount));
        assertConnectionUnencrypted("Console failed, does not show only Encrypted connections", items);

        addressSpaceConsoleWebPage.clearAllFilters();
        assertThat(addressSpaceConsoleWebPage.getConnectionItems(receiverCount).size(), is(receiverCount));

        addressSpaceConsoleWebPage.addConnectionsFilter(FilterType.ENCRYPTED, "unencrypted");
        items = addressSpaceConsoleWebPage.getConnectionItems();
        assertThat(String.format("Console failed, does not contain %d connections", 0),
                items.size(), is(0));
        assertConnectionEncrypted("Console failed, does not show only Encrypted connections", items);
    }

    protected void doTestFilterConnectionsByUser() throws Exception {
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        Address queue = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue-connection-users"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("queue-connection-users")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        addressSpaceConsoleWebPage.createAddressesWebConsole(queue);
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();

        UserCredentials pavel = new UserCredentials("pavel", "enmasse");
        resourcesManager.createOrUpdateUser(getSharedAddressSpace(), pavel);
        List<ExternalMessagingClient> receiversPavel = null;
        List<ExternalMessagingClient> receiversTest = null;
        try {
            int receiversBatch1 = 5;
            int receiversBatch2 = 10;
            receiversPavel = getClientUtils().attachReceivers(getSharedAddressSpace(), queue, receiversBatch1, -1, pavel);
            receiversTest = getClientUtils().attachReceivers(getSharedAddressSpace(), queue, receiversBatch2, -1, defaultCredentials);
            assertThat(String.format("Console failed, does not contain %d connections", receiversBatch1 + receiversBatch2),
                    addressSpaceConsoleWebPage.getConnectionItems(receiversBatch1 + receiversBatch2).size(), is(receiversBatch1 + receiversBatch2));

            addressSpaceConsoleWebPage.addConnectionsFilter(FilterType.USER, defaultCredentials.getUsername());
            List<ConnectionWebItem> items = addressSpaceConsoleWebPage.getConnectionItems(receiversBatch2);
            assertThat(String.format("Console failed, does not contain %d connections", receiversBatch2),
                    items.size(), is(receiversBatch2));
            assertConnectionUsers(
                    String.format("Console failed, does not contain connections for user '%s'", defaultCredentials),
                    items, defaultCredentials.getUsername());

            addressSpaceConsoleWebPage.addConnectionsFilter(FilterType.USER, pavel.getUsername());
            assertThat(String.format("Console failed, does not contain %d connections", 0),
                    addressSpaceConsoleWebPage.getConnectionItems().size(), is(0));

            addressSpaceConsoleWebPage.removeFilterByUser(defaultCredentials.getUsername());
            items = addressSpaceConsoleWebPage.getConnectionItems(receiversBatch1);
            assertThat(String.format("Console failed, does not contain %d connections", receiversBatch1),
                    items.size(), is(receiversBatch1));
            assertConnectionUsers(
                    String.format("Console failed, does not contain connections for user '%s'", pavel),
                    items, pavel.getUsername());

            addressSpaceConsoleWebPage.clearAllFilters();
            assertThat(String.format("Console failed, does not contain %d connections", receiversBatch1 + receiversBatch2),
                    addressSpaceConsoleWebPage.getConnectionItems(receiversBatch1 + receiversBatch2).size(), is(receiversBatch1 + receiversBatch2));
        } finally {
            resourcesManager.removeUser(getSharedAddressSpace(), pavel.getUsername());
            getClientUtils().stopClients(receiversTest);
            getClientUtils().stopClients(receiversPavel);
        }

    }

    protected void doTestFilterConnectionsByHostname() throws Exception {
        int addressCount = 2;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();

        clientsList = attachClients(addresses);

        List<ConnectionWebItem> connectionItems = addressSpaceConsoleWebPage.getConnectionItems(6);
        String hostname = connectionItems.get(0).getName();

        addressSpaceConsoleWebPage.addConnectionsFilter(FilterType.HOSTNAME, hostname);
        assertThat(String.format("Console failed, does not contain %d connections", 1),
                addressSpaceConsoleWebPage.getConnectionItems(1).size(), is(1));

        addressSpaceConsoleWebPage.clearAllFilters();
        assertThat(String.format("Console failed, does not contain %d connections", 6),
                addressSpaceConsoleWebPage.getConnectionItems(6).size(), is(6));
    }

    protected void doTestSortConnectionsByHostname() throws Exception {
        int addressCount = 2;
        ArrayList<Address> addresses = generateQueueTopicList(getSharedAddressSpace(), "via-web", IntStream.range(0, addressCount));
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(addresses.toArray(new Address[0]));
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();

        clientsList = attachClients(addresses);

        addressSpaceConsoleWebPage.sortItems(SortType.HOSTNAME, true);
        assertSorted("Console failed, items are not sorted by hostname asc",
                addressSpaceConsoleWebPage.getConnectionItems(), Comparator.comparing(ConnectionWebItem::getName));

        addressSpaceConsoleWebPage.sortItems(SortType.HOSTNAME, false);
        assertSorted("Console failed, items are not sorted by hostname desc",
                addressSpaceConsoleWebPage.getConnectionItems(), true, Comparator.comparing(ConnectionWebItem::getName));
    }

    protected void doTestFilterConnectionsByContainerId() throws Exception {
        int connectionCount = 5;

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue-via-web"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("queue-via-web")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        addressSpaceConsoleWebPage.createAddressWebConsole(dest);
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();

        clientsList = new ArrayList<>();
        clientsList.add(getClientUtils().attachConnector(getSharedAddressSpace(), dest, connectionCount, 1, 1, defaultCredentials, 360));
        selenium.waitUntilPropertyPresent(60, connectionCount, () -> addressSpaceConsoleWebPage.getConnectionItems().size());

        String containerID = addressSpaceConsoleWebPage.getConnectionItems(connectionCount).get(0).getContainerID();

        addressSpaceConsoleWebPage.addConnectionsFilter(FilterType.CONTAINER, containerID);
        assertThat(String.format("Console failed, does not contain %d connections", 1),
                addressSpaceConsoleWebPage.getConnectionItems(1).size(), is(1));

        addressSpaceConsoleWebPage.clearAllFilters();
        assertThat(String.format("Console failed, does not contain %d connections", connectionCount),
                addressSpaceConsoleWebPage.getConnectionItems(connectionCount).size(), is(connectionCount));
    }

    protected void doTestSortConnectionsByContainerId() throws Exception {
        int connectionCount = 5;

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue-via-web"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("queue-via-web")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        addressSpaceConsoleWebPage.createAddressWebConsole(dest);
        addressSpaceConsoleWebPage.openConnectionsPageWebConsole();

        clientsList = new ArrayList<>();
        clientsList.add(getClientUtils().attachConnector(getSharedAddressSpace(), dest, connectionCount, 1, 1, defaultCredentials, 360));

        selenium.waitUntilPropertyPresent(60, connectionCount, () -> addressSpaceConsoleWebPage.getConnectionItems().size());

        addressSpaceConsoleWebPage.sortItems(SortType.CONTAINER_ID, true);
        assertSorted("Console failed, items are not sorted by containerID asc",
                addressSpaceConsoleWebPage.getConnectionItems(), Comparator.comparing(ConnectionWebItem::getContainerID));

        addressSpaceConsoleWebPage.sortItems(SortType.CONTAINER_ID, false);
        assertSorted("Console failed, items are not sorted by containerID desc",
                addressSpaceConsoleWebPage.getConnectionItems(), true, Comparator.comparing(ConnectionWebItem::getContainerID));
    }

    protected void doTestMessagesMetrics() throws Exception {
        int msgCount = 19;
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue-via-web"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("queue-via-web")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        addressSpaceConsoleWebPage.createAddressWebConsole(dest);
        addressSpaceConsoleWebPage.openAddressesPageWebConsole();

        AmqpClient client = resourcesManager.getAmqpClientFactory().createQueueClient();
        List<String> msgBatch = TestUtils.generateMessages(msgCount);

        int sent = client.sendMessages(dest.getSpec().getAddress(), msgBatch).get(2, TimeUnit.MINUTES);
        selenium.waitUntilPropertyPresent(60, msgCount, () -> addressSpaceConsoleWebPage.getAddressItem(dest).getMessagesIn());
        assertEquals(sent, addressSpaceConsoleWebPage.getAddressItem(dest).getMessagesIn(),
                String.format("Console failed, does not contain %d messagesIN", sent));

        selenium.waitUntilPropertyPresent(60, msgCount, () -> addressSpaceConsoleWebPage.getAddressItem(dest).getMessagesStored());
        assertEquals(msgCount, addressSpaceConsoleWebPage.getAddressItem(dest).getMessagesStored(),
                String.format("Console failed, does not contain %d messagesStored", msgCount));

        int received = client.recvMessages(dest.getSpec().getAddress(), msgCount).get(1, TimeUnit.MINUTES).size();
        selenium.waitUntilPropertyPresent(60, msgCount, () -> addressSpaceConsoleWebPage.getAddressItem(dest).getMessagesOut());
        assertEquals(received, addressSpaceConsoleWebPage.getAddressItem(dest).getMessagesOut(),
                String.format("Console failed, does not contain %d messagesOUT", received));

    }

    protected void doTestClientsMetrics() throws Exception {
        int senderCount = 5;
        int receiverCount = 10;
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue-via-web"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("queue-via-web")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        addressSpaceConsoleWebPage.createAddressWebConsole(dest);
        addressSpaceConsoleWebPage.openAddressesPageWebConsole();

        ExternalMessagingClient client = new ExternalMessagingClient()
                .withClientEngine(new RheaClientConnector());
        try {
            client = getClientUtils().attachConnector(getSharedAddressSpace(), dest, 1, senderCount, receiverCount, defaultCredentials, 360);
            selenium.waitUntilPropertyPresent(60, senderCount, () -> addressSpaceConsoleWebPage.getAddressItem(dest).getSendersCount());

            assertAll(
                    () -> assertEquals(10, addressSpaceConsoleWebPage.getAddressItem(dest).getReceiversCount(),
                            String.format("Console failed, does not contain %d receivers", 10)),
                    () -> assertEquals(5, addressSpaceConsoleWebPage.getAddressItem(dest).getSendersCount(),
                            String.format("Console failed, does not contain %d senders", 5)));
        } finally {
            client.stop();
        }
    }

    protected void doTestCanOpenConsolePage(UserCredentials credentials, boolean userAllowed) throws Exception {
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), credentials);
        addressSpaceConsoleWebPage.openWebConsolePage();
        log.info("User {} successfully authenticated", credentials);

        if (userAllowed) {
            addressSpaceConsoleWebPage.openAddressesPageWebConsole();
        } else {
            addressSpaceConsoleWebPage.assertDialogPresent("noRbacErrorDialog");

            try {
                addressSpaceConsoleWebPage.openAddressesPageWebConsole();
                fail("Exception not thrown");
            } catch (WebDriverException ex) {
                // PASS
            }

            throw new IllegalAccessException();
        }
    }

    protected void doTestWithStrangeAddressNames(boolean hyphen, boolean longName, AddressType... types) throws Exception {
        int assert_value = 1;
        String testString = null;
        Address dest;
        Address dest_topic = null;
        if (hyphen) {
            testString = String.join("-", Collections.nCopies(9, "10charhere"));
        }
        if (longName) {
            testString = String.join("", Collections.nCopies(24, "10charhere"));
        }

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();

        for (AddressType type : types) {
            if (type == AddressType.SUBSCRIPTION) {
                dest_topic = new AddressBuilder()
                        .withNewMetadata()
                        .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                        .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "topic-sub" + testString))
                        .endMetadata()
                        .withNewSpec()
                        .withType("topic")
                        .withAddress("topic-sub" + testString)
                        .withPlan(getDefaultPlan(AddressType.TOPIC))
                        .endSpec()
                        .build();
                log.info("Creating topic for subscription");
                addressSpaceConsoleWebPage.createAddressWebConsole(dest_topic);
                dest = new AddressBuilder()
                        .withNewMetadata()
                        .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                        .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), testString))
                        .endMetadata()
                        .withNewSpec()
                        .withType("subscription")
                        .withAddress(testString)
                        .withTopic(dest_topic.getSpec().getAddress())
                        .withPlan(DestinationPlan.STANDARD_SMALL_SUBSCRIPTION)
                        .endSpec()
                        .build();
                assert_value = 2;
            } else {
                dest = new AddressBuilder()
                        .withNewMetadata()
                        .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                        .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), type.toString() + "-" + testString))
                        .endMetadata()
                        .withNewSpec()
                        .withType(type.toString())
                        .withAddress(type.toString() + "-" + testString)
                        .withPlan(getDefaultPlan(type))
                        .endSpec()
                        .build();
            }

            addressSpaceConsoleWebPage.createAddressWebConsole(dest);
            assertWaitForValue(assert_value, () -> addressSpaceConsoleWebPage.getResultsCount(), new TimeoutBudget(120, TimeUnit.SECONDS));

            if (type.equals(AddressType.SUBSCRIPTION)) {
                addressSpaceConsoleWebPage.deleteAddressWebConsole(dest_topic);
            }
            addressSpaceConsoleWebPage.deleteAddressWebConsole(dest);
            assertWaitForValue(0, () -> addressSpaceConsoleWebPage.getResultsCount(), new TimeoutBudget(20, TimeUnit.SECONDS));
        }
    }

    protected void doTestCreateAddressWithSpecialCharsShowsErrorMessage() throws Exception {
        final Supplier<WebElement> webElementSupplier = () -> selenium.getDriver().findElement(By.id("new-name"));
        String testString = "addressname";
        Address destValid = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), testString))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress(testString)
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.openAddressesPageWebConsole();
        addressSpaceConsoleWebPage.clickOnCreateButton();

        for (char special_char : "#*/.:".toCharArray()) {
            //fill with valid name first
            selenium.fillInputItem(selenium.getWebElement(webElementSupplier), destValid.getSpec().getAddress());
            WebElement helpBlock = selenium.getWebElement(() -> selenium.getDriver().findElement(By.className("help-block")));
            assertTrue(helpBlock.getText().isEmpty());

            //fill with invalid name (including spec_char)
            selenium.fillInputItem(selenium.getWebElement(webElementSupplier), testString + special_char);
            assertTrue(helpBlock.isDisplayed());
        }
    }

    protected void doTestCreateAddressWithSymbolsAt61stCharIndex(Address... destinations) throws Exception {
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.openAddressesPageWebConsole();

        for (Address dest : destinations) {
            addressSpaceConsoleWebPage.createAddressWebConsole(dest);
            addressSpaceConsoleWebPage.deleteAddressWebConsole(dest);
        }
        assertWaitForValue(0, () -> addressSpaceConsoleWebPage.getResultsCount(), new TimeoutBudget(20, TimeUnit.SECONDS));
    }

    protected void doTestAddressWithValidPlanOnly() throws Exception {
        Address destQueue = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue-via-web"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("queue-via-web")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();

        Address destTopic = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "topic-via-web"))
                .endMetadata()
                .withNewSpec()
                .withType("topic")
                .withAddress("topic-via-web")
                .withPlan(getDefaultPlan(AddressType.TOPIC))
                .endSpec()
                .build();

        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.openAddressesPageWebConsole();

        // create Queue with default Plan and move to confirmation page
        selenium.clickOnItem(addressSpaceConsoleWebPage.getCreateButton(), "clicking on create button");
        final Supplier<WebElement> webElementSupplier = () -> selenium.getDriver().findElement(By.id("new-name"));
        selenium.fillInputItem(selenium.getWebElement(webElementSupplier), destQueue.getSpec().getAddress());
        selenium.clickOnItem(addressSpaceConsoleWebPage.getRadioButtonForAddressType(destQueue), "clicking on radio button");
        addressSpaceConsoleWebPage.next();
        addressSpaceConsoleWebPage.next();

        // go back to page 1 by clicking "number 1"
        addressSpaceConsoleWebPage.clickOnAddressModalPageByNumber(1);

        // change details to Topic
        selenium.fillInputItem(selenium.getWebElement(webElementSupplier), destTopic.getSpec().getAddress());
        selenium.clickOnItem(addressSpaceConsoleWebPage.getRadioButtonForAddressType(destTopic), "clicking on radio button");

        // skip straight back to page 3 and create address
        addressSpaceConsoleWebPage.clickOnAddressModalPageByNumber(3);
        addressSpaceConsoleWebPage.next();

        // assert new address is Topic
        assertEquals(AddressType.TOPIC.toString(),
                selenium.waitUntilItemPresent(60, () -> addressSpaceConsoleWebPage.getAddressItem(destTopic)).getType(),
                "Console failed, expected TOPIC type");


        AddressUtils.waitForDestinationsReady(destTopic);

        getClientUtils().assertCanConnect(getSharedAddressSpace(), defaultCredentials, Collections.singletonList(destTopic), resourcesManager);
    }

    protected void doTestPurgeMessages(Address address) throws Exception {
        List<String> msgs = IntStream.range(0, 1000).mapToObj(i -> "msgs:" + i).collect(Collectors.toList());
        addressSpaceConsoleWebPage = new AddressSpaceConsoleWebPage(selenium, AddressSpaceUtils.getConsoleRoute(getSharedAddressSpace()),
                getSharedAddressSpace(), clusterUser);
        addressSpaceConsoleWebPage.openWebConsolePage();
        addressSpaceConsoleWebPage.createAddressesWebConsole(address);
        AmqpClient client = getAmqpClientFactory().createQueueClient();

        Future<Integer> sendResult = client.sendMessages(address.getSpec().getAddress(), msgs);
        assertThat("Wrong count of messages sent", sendResult.get(1, TimeUnit.MINUTES), is(msgs.size()));

        Future<List<Message>> recvResult = client.recvMessages(address.getSpec().getAddress(), msgs.size() / 2);
        assertThat("Wrong count of messages receiver", recvResult.get(1, TimeUnit.MINUTES).size(), is(msgs.size() / 2));

        addressSpaceConsoleWebPage.openAddressesPageWebConsole();
        addressSpaceConsoleWebPage.purgeAddress(address);

        Future<List<Message>> recvResult2 = client.recvMessages(address.getSpec().getAddress(), msgs.size() / 2);
        assertThrows(TimeoutException.class, () -> recvResult2.get(20, TimeUnit.SECONDS), "Purge does not work, address contains messages");
    }

    //============================================================================================
    //============================ Help methods ==================================================
    //============================================================================================

    private ArrayList<Address> generateQueueTopicList(AddressSpace addressspace, String infix, IntStream range) {
        ArrayList<Address> addresses = new ArrayList<>();
        range.forEach(i -> {
            if (i % 2 == 0) {
                addresses.add(new AddressBuilder()
                        .withNewMetadata()
                        .withNamespace(addressspace.getMetadata().getNamespace())
                        .withName(AddressUtils.generateAddressMetadataName(addressspace, String.format("topic-%s-%d", infix, i)))
                        .endMetadata()
                        .withNewSpec()
                        .withType("topic")
                        .withAddress(String.format("topic-%s-%d", infix, i))
                        .withPlan(getDefaultPlan(AddressType.TOPIC))
                        .endSpec()
                        .build());
            } else {
                addresses.add(new AddressBuilder()
                        .withNewMetadata()
                        .withNamespace(addressspace.getMetadata().getNamespace())
                        .withName(AddressUtils.generateAddressMetadataName(addressspace, String.format("queue-%s-%d", infix, i)))
                        .endMetadata()
                        .withNewSpec()
                        .withType("queue")
                        .withAddress(String.format("queue-%s-%d", infix, i))
                        .withPlan(getDefaultPlan(AddressType.QUEUE))
                        .endSpec()
                        .build());
            }
        });
        return addresses;
    }

    private List<ExternalMessagingClient> attachClients(List<Address> destinations) throws Exception {
        List<ExternalMessagingClient> clients = new ArrayList<>();
        for (Address destination : destinations) {
            clients.add(getClientUtils().attachConnector(getSharedAddressSpace(), destination, 1, 6, 1, defaultCredentials, 360));
            clients.add(getClientUtils().attachConnector(getSharedAddressSpace(), destination, 1, 4, 4, defaultCredentials, 360));
            clients.add(getClientUtils().attachConnector(getSharedAddressSpace(), destination, 1, 1, 6, defaultCredentials, 360));
        }

        Thread.sleep(5000);

        return clients;
    }


    private void assertAddressType(String message, List<AddressWebItem> allItems, AddressType type) {
        assertThat(message, getAddressProperty(allItems, (item -> item.getType().contains(type.toString()))).size(), is(allItems.size()));
    }

    private void assertAddressName(String message, List<AddressWebItem> allItems, String subString) {
        assertThat(message, getAddressProperty(allItems, (item -> item.getName().contains(subString))).size(), is(allItems.size()));
    }

    private void assertConnectionEncrypted(String message, List<ConnectionWebItem> allItems) {
        assertThat(message, getConnectionProperty(allItems, (ConnectionWebItem::isEncrypted)).size(), is(allItems.size()));
    }

    private void assertConnectionUnencrypted(String message, List<ConnectionWebItem> allItems) {
        assertThat(message, getConnectionProperty(allItems, (item -> !item.isEncrypted())).size(), is(allItems.size()));
    }

    private void assertConnectionUsers(String message, List<ConnectionWebItem> allItems, String userName) {
        assertThat(message, getConnectionProperty(allItems, (item -> item.getUser().contains(userName))).size(), is(allItems.size()));
    }

    private List<ConnectionWebItem> getConnectionProperty(List<ConnectionWebItem> allItems, Predicate<ConnectionWebItem> f) {
        return allItems.stream().filter(f).collect(Collectors.toList());
    }

    private List<AddressWebItem> getAddressProperty(List<AddressWebItem> allItems, Predicate<AddressWebItem> f) {
        return allItems.stream().filter(f).collect(Collectors.toList());
    }
}
