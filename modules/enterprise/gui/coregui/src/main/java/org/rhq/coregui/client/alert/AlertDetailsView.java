/*
 * RHQ Management Platform
 * Copyright (C) 2005-2013 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package org.rhq.coregui.client.alert;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.core.DataClass;
import com.smartgwt.client.data.Record;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.ListGridFieldType;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.fields.FormItem;
import com.smartgwt.client.widgets.form.fields.StaticTextItem;
import com.smartgwt.client.widgets.grid.CellFormatter;
import com.smartgwt.client.widgets.grid.HoverCustomizer;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.tab.Tab;
import com.smartgwt.client.widgets.tab.TabSet;

import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.alert.notification.ResultState;
import org.rhq.core.domain.criteria.AlertCriteria;
import org.rhq.core.domain.criteria.AlertDefinitionCriteria;
import org.rhq.core.domain.util.PageList;
import org.rhq.coregui.client.BookmarkableView;
import org.rhq.coregui.client.CoreGUI;
import org.rhq.coregui.client.ImageManager;
import org.rhq.coregui.client.LinkManager;
import org.rhq.coregui.client.ViewPath;
import org.rhq.coregui.client.components.tab.NamedTab;
import org.rhq.coregui.client.components.tab.NamedTabSet;
import org.rhq.coregui.client.components.table.Table;
import org.rhq.coregui.client.components.table.TimestampCellFormatter;
import org.rhq.coregui.client.components.view.ViewName;
import org.rhq.coregui.client.gwt.GWTServiceLookup;
import org.rhq.coregui.client.util.Log;
import org.rhq.coregui.client.util.enhanced.EnhancedVLayout;

/**
 * @author Joseph Marques
 * @author Ian Springer
 */
public class AlertDetailsView extends EnhancedVLayout implements BookmarkableView {

    private int alertId;

    private static AlertDetailsView INSTANCE = new AlertDetailsView();

    public static AlertDetailsView getInstance() {
        return INSTANCE;
    }

    private AlertDetailsView() {
        // access through the static singleton only
        super();
    }

    private void show(int alertId) {
        AlertCriteria criteria = new AlertCriteria();
        criteria.addFilterId(alertId);
        GWTServiceLookup.getAlertService().findAlertsByCriteria(criteria, new AsyncCallback<PageList<Alert>>() {
            @Override
            public void onSuccess(PageList<Alert> result) {
                Alert alert = result.get(0);
                Integer parentId = alert.getAlertDefinition().getParentId();
                AlertDefinition groupAlertDefinition = alert.getAlertDefinition().getGroupAlertDefinition();
                if (groupAlertDefinition != null || (parentId != null && parentId.intValue() != 0)) {
                    fetchDefinitionWithGroupAndTemplate(alert);
                } else {
                    show(alert);
                }

            }

            @Override
            public void onFailure(Throwable caught) {
                CoreGUI.getErrorHandler().handleError(MSG.view_alert_details_loadFailed(), caught);
            }
        });
    }

    private void fetchDefinitionWithGroupAndTemplate(final Alert alert) {
        AlertDefinitionCriteria criteria = new AlertDefinitionCriteria();
        criteria.addFilterAlertId(alert.getId());
        criteria.fetchGroupAlertDefinition(true);
        criteria.fetchResourceType(true);
        GWTServiceLookup.getAlertDefinitionService().findAlertDefinitionsByCriteria(criteria, new AsyncCallback<PageList<AlertDefinition>>() {
            public void onSuccess(PageList<AlertDefinition> result) {
                alert.getAlertDefinition().setGroupAlertDefinition(result.get(0).getGroupAlertDefinition());
                alert.getAlertDefinition().setResourceType(result.get(0).getResourceType());
                show(alert);
            }

            public void onFailure(Throwable caught) {
                CoreGUI.getErrorHandler().handleError(MSG.view_alert_details_loadFailed(), caught);
            }
        });
    }

    private void show(Alert alert) {
        destroyMembers();

        Log.info("AlertDetailsView-Alert: " + alert);
        ListGridRecord record = AlertDataSource.convert(alert);
        Log.info("AlertDetailsView-ListGridRecord: " + record);
        addMember(getDetailsTabSet(record));
    }

    private TabSet getDetailsTabSet(ListGridRecord record) {
        TabSet tabset = new NamedTabSet();

        Tab generalTab = new NamedTab(new ViewName("general", MSG.common_title_generalProp()));
        generalTab.setPane(getDetailsTableForAlert(record));

        Tab conditionsTab = new NamedTab(new ViewName("conditions", MSG.view_alert_common_tab_conditions()));
        conditionsTab.setPane(getConditionsForAlert(record));

        Tab notificationsTab = new NamedTab(new ViewName("notifications", MSG.view_alert_common_tab_notifications()));
        notificationsTab.setPane(getNotificationsForAlert(record));

        tabset.addTab(generalTab);
        tabset.addTab(conditionsTab);
        tabset.addTab(notificationsTab);

        return tabset;
    }

    private DynamicForm getDetailsTableForAlert(ListGridRecord record) {
        DynamicForm form = new DynamicForm();
        form.setNumCols(4);
        form.setHeight("15%");
        form.setWrapItemTitles(false);
        form.setAlign(Alignment.LEFT);

        List<FormItem> items = new ArrayList<FormItem>();

        StaticTextItem nameTextItem = new StaticTextItem("name", MSG.common_title_name());
        nameTextItem.setValue(record.getAttribute("name"));
        nameTextItem.setTooltip("Id = " + record.getAttribute("id"));
        items.add(nameTextItem);

        StaticTextItem descriptionTextItem = new StaticTextItem("description", MSG.common_title_description());
        descriptionTextItem.setValue(record.getAttribute("description"));
        items.add(descriptionTextItem);

        StaticTextItem prioTextItem = new StaticTextItem("priority", MSG.view_alerts_field_priority());
        prioTextItem.setValue(record.getAttribute("priority"));
        LinkedHashMap<String, String> priorityIcons = new LinkedHashMap<String, String>(3);
        priorityIcons.put(AlertDataSource.PRIORITY_ICON_HIGH, AlertDataSource.PRIORITY_ICON_HIGH);
        priorityIcons.put(AlertDataSource.PRIORITY_ICON_MEDIUM, AlertDataSource.PRIORITY_ICON_MEDIUM);
        priorityIcons.put(AlertDataSource.PRIORITY_ICON_LOW, AlertDataSource.PRIORITY_ICON_LOW);
        prioTextItem.setValueIcons(priorityIcons);
        // shouldn't have to do this, but the data source stores the actual URL of the icon in it, we need to map back to value
        LinkedHashMap<String, String> priorityMap = new LinkedHashMap<String, String>(3);
        priorityMap.put(AlertDataSource.PRIORITY_ICON_HIGH, MSG.common_alert_high());
        priorityMap.put(AlertDataSource.PRIORITY_ICON_MEDIUM, MSG.common_alert_medium());
        priorityMap.put(AlertDataSource.PRIORITY_ICON_LOW, MSG.common_alert_low());
        prioTextItem.setValueMap(priorityMap);
        items.add(prioTextItem);

        StaticTextItem createdTextItem = new StaticTextItem("ctime", MSG.common_title_createTime());
        createdTextItem.setValue(TimestampCellFormatter.format(record.getAttributeAsDate("ctime")));
        items.add(createdTextItem);

        StaticTextItem ackByItem = new StaticTextItem("acknowledgingSubject", MSG.view_alert_details_field_ack_by());
        if (record.getAttribute("acknowledgingSubject") != null) {
            ackByItem.setValue(record.getAttribute("acknowledgingSubject"));
        } else {
            ackByItem.setValue(MSG.view_alerts_field_ack_status_noAck());
        }
        items.add(ackByItem);

        StaticTextItem ackTimeItem = new StaticTextItem("acknowledgeTime", MSG.view_alert_details_field_ack_at());
        Date ack_time = record.getAttributeAsDate("acknowledgeTime");
        if (ack_time != null) {
            ackTimeItem.setValue(TimestampCellFormatter.format(ack_time));
        } else {
            ackTimeItem.setValue(MSG.view_alerts_field_ack_status_noAck());
        }
        items.add(ackTimeItem);

        StaticTextItem recoveryItem = new StaticTextItem("recovery", MSG.view_alert_details_field_recovery_info());
        recoveryItem.setValue(record.getAttribute("recoveryInfo"));
        items.add(recoveryItem);

        StaticTextItem resourceItem = new StaticTextItem("resourceName",
            MSG.view_alert_details_field_watched_resource());
        String resourceUrl = LinkManager.getResourceLink(Integer.parseInt(record.getAttribute("resourceId")));
        resourceItem.setValue(LinkManager.getHref(resourceUrl, record.getAttribute("resourceName")));
        items.add(resourceItem);

        StaticTextItem resourceAncestryItem = new StaticTextItem("resourceAncestry",
            MSG.view_alert_details_field_resource_ancestry());
        resourceAncestryItem.setValue(record.getAttribute("resourceAncestry"));
        items.add(resourceAncestryItem);

        String parentUrl = record.getAttribute("parent");
        if (parentUrl != null) {
            StaticTextItem parentItem = new StaticTextItem("parent", "Parent Definition");
            parentItem.setValue(LinkManager.getHref(parentUrl, record.getLinkText()));
            items.add(parentItem);
        }

        form.setItems(items.toArray(new FormItem[items.size()]));

        return form;
    }

    @SuppressWarnings("unchecked")
    private class NotificationLogsTable extends Table {
        private final Record record;

        public NotificationLogsTable(String tableTitle, Record record) {
            super(tableTitle, false);
            this.record = record;
            setHeight("35%");
            setWidth100();
            setShowFooterRefresh(false);
        }

        @Override
        protected void configureTable() {
            DataClass[] input = record.getAttributeAsRecordArray("notificationLogs");
            ListGrid grid = getListGrid();
            grid.setData((Record[]) input);

            ListGridField sender = new ListGridField("sender", MSG.view_alert_common_tab_notifications_sender());
            sender.setWidth("33%");

            ListGridField status = new ListGridField("status", MSG.view_alert_common_tab_notifications_status());
            status.setWidth("50");
            status.setAlign(Alignment.CENTER);
            status.setType(ListGridFieldType.IMAGE);
            status.setCellFormatter(new CellFormatter() {
                public String format(Object value, ListGridRecord record, int rowNum, int colNum) {
                    String statusStr = record.getAttribute("status");
                    ResultState statusEnum = (statusStr == null) ? ResultState.UNKNOWN : ResultState.valueOf(statusStr);
                    return imgHTML(ImageManager.getAlertNotificationResultIcon(statusEnum));
                }
            });
            status.setShowHover(true);
            status.setHoverCustomizer(new HoverCustomizer() {
                public String hoverHTML(Object value, ListGridRecord record, int rowNum, int colNum) {
                    String statusStr = record.getAttribute("status");
                    ResultState statusEnum = (statusStr == null) ? ResultState.UNKNOWN : ResultState.valueOf(statusStr);
                    switch (statusEnum) {
                    case SUCCESS:
                        return MSG.common_status_success();
                    case FAILURE:
                        return MSG.common_status_failed();
                    case PARTIAL:
                        return MSG.common_status_partial();
                    case DEFERRED:
                        return MSG.common_status_deferred();
                    case UNKNOWN:
                    default:
                        return MSG.common_status_unknown();
                    }
                }
            });

            ListGridField message = new ListGridField("message", MSG.view_alert_common_tab_notifications_message());
            message.setWidth("*");

            grid.setFields(sender, status, message);
        }
    }

    @SuppressWarnings("unchecked")
    private Table getNotificationsForAlert(Record record) {
        Table notifTable = new NotificationLogsTable(MSG.view_alert_common_tab_notifications(), record);
        return notifTable;
    }

    @SuppressWarnings("unchecked")
    private class ConditionLogsTable extends Table {
        private final Record record;

        public ConditionLogsTable(String tableTitle, Record record) {
            super(tableTitle, false);
            this.record = record;
            setHeight("35%");
            setWidth100();
            setShowFooterRefresh(false);
        }

        @Override
        protected void configureTable() {
            DataClass[] input = record.getAttributeAsRecordArray("conditionLogs");
            ListGrid grid = getListGrid();
            grid.setData((Record[]) input);
            ListGridField condition = new ListGridField("text", MSG.view_alert_common_tab_conditions_text());
            condition.setWidth("60%");
            ListGridField value = new ListGridField("value", MSG.common_title_value());

            grid.setFields(condition, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Table getConditionsForAlert(Record record) {
        String mode = record.getAttribute("conditionExpression");
        Table table = new ConditionLogsTable(MSG.view_alert_common_tab_conditions() + ": match = " + mode, record);
        return table;
    }

    @Override
    public void renderView(ViewPath viewPath) {
        alertId = viewPath.getCurrentAsInt();

        show(alertId);
    }

}
