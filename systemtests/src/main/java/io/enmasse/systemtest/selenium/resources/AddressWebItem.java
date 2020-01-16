/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.selenium.resources;

import io.enmasse.systemtest.model.address.AddressStatus;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class AddressWebItem extends WebItem implements Comparable<AddressWebItem>{
    private WebElement checkBox;
    private String name;
    private String plan;
    private WebElement clientsRoute;
    private int messagesIn;
    private int messagesOut;
    private int messagesStored;
    private int senders;
    private int receivers;
    private int shards;
    private WebElement actionDropDown;

    public AddressWebItem(WebElement item) {
        this.webItem = item;
        this.checkBox = webItem.findElement(By.xpath("./td[@data-key='0']")).findElement(By.tagName("input"));
        this.name = parseName(webItem.findElement(By.xpath("./td[@data-label='Name']")));
        this.clientsRoute = parseRoute(webItem.findElement(By.xpath("./td[@data-label='Name']")));
        this.plan = webItem.findElement(By.xpath("./td[@data-label='Type/Plan']")).getText().split(" ")[1];
        this.messagesIn = Integer.parseInt(webItem.findElement(By.xpath("./td[@data-label='column-3']")).getText());
        this.messagesOut = Integer.parseInt(webItem.findElement(By.xpath("./td[@data-label='column-4']")).getText());
        this.messagesStored = Integer.parseInt(webItem.findElement(By.xpath("./td[@data-label='Stored Messages']")).getText());
        this.senders = Integer.parseInt(webItem.findElement(By.xpath("./td[@data-label='Senders']")).getText());
        this.receivers = Integer.parseInt(webItem.findElement(By.xpath("./td[@data-label='Receivers']")).getText());
        this.shards = Integer.parseInt(webItem.findElement(By.xpath("./td[@data-label='Shards']")).getText());
        this.actionDropDown = webItem.findElement(By.className("pf-c-dropdown"));
    }

    public WebElement getCheckBox() {
        return checkBox;
    }

    public String getName() {
        return name;
    }

    public WebElement getClientsRoute() {
        return clientsRoute;
    }

    public String getPlan() {
        return plan;
    }

    public int getMessagesIn() {
        return messagesIn;
    }

    public int getMessagesOut() {
        return messagesOut;
    }

    public int getMessagesStored() {
        return messagesStored;
    }

    public int getSendersCount() {
        return senders;
    }

    public int getReceiversCount() {
        return receivers;
    }

    public int getShards() {
        return shards;
    }

    public AddressStatus getStatus() {
        return AddressStatus.READY;
    }

    public String getType() {
        return "";
    }

    public WebElement getActionDropDown() {
        return actionDropDown;
    }

    private String parseName(WebElement elem) {
        try {
            return elem.findElement(By.tagName("a")).getText();
        } catch (Exception ex) {
            return elem.findElements(By.tagName("p")).get(0).getText();
        }
    }

    private WebElement parseRoute(WebElement elem) {
        try {
            return elem.findElement(By.tagName("a"));
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("name: %s, plan: %s, messagesIn: %d, messagesOut: %d, stored: %d, senders: %d, receivers: %d, shards: %d,",
                this.name,
                this.plan,
                this.messagesIn,
                this.messagesOut,
                this.messagesStored,
                this.senders,
                this.receivers,
                this.shards);
    }

    @Override
    public int compareTo(AddressWebItem o) {
        return name.compareTo(o.name);
    }
}
