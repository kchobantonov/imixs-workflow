package org.imixs.workflow.bpmn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.imixs.workflow.ItemCollection;
import org.openbpmn.bpmn.BPMNModel;
import org.openbpmn.bpmn.BPMNNS;
import org.openbpmn.bpmn.elements.Activity;
import org.openbpmn.bpmn.elements.DataObject;
import org.openbpmn.bpmn.elements.Event;
import org.openbpmn.bpmn.elements.Signal;
import org.openbpmn.bpmn.elements.core.BPMNElement;
import org.openbpmn.bpmn.elements.core.BPMNElementNode;
import org.openbpmn.bpmn.navigation.BPMNFlowIterator;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This OpenBPMNEntityBuilder provides methods to convert a OpenBPMN
 * BPMNElementNode into a ItemCollection. The corresponding ItemCollection
 * contains all Imixs Extension Elements as also the items 'id' and 'type'.
 * <p>
 * Through the item 'id' it is possible to access the BPMN element directly from
 * an ItemCollection by the BPMN element id which should be unique. In addition,
 * the item "type" is set to 'TASK' or 'EVENT', which reflects the
 * type of an element.
 * <p>
 * Example:
 * 
 * <pre>{@code  
<bpmn2:task id="Task_2" imixs:processid="1900" name="Approve">
    <bpmn2:extensionElements>
    <imixs:item name="user.name" type="xs:string">John</imixs:item>
    ....
    </bpmn2:extensionElements>
    ...
</bpmn2:task>
        }</pre>
 * 
 */
public class OpenBPMNEntityBuilder {

    private static Logger logger = Logger.getLogger(OpenBPMNEntityBuilder.class.getName());

    /**
     * Private constructor to prevent instantiation
     */
    private OpenBPMNEntityBuilder() {
    }

    /**
     * This method converts a Imixs BPMNElement into an ItemCollection
     * All imixs Extension values will be added as items.
     * 
     * Example:
     * 
     * <pre>
     * {@code
        <bpmn2:extensionElements>
          <imixs:item name="txttype" type="xs:string">
            <imixs:value><![CDATA[workitemarchive]]></imixs:value>
          </imixs:item>
        ....
     * }
     * </pre>
     * 
     * @param bpmnElement
     * @return
     */
    public static ItemCollection build(BPMNElementNode bpmnElement) {
        ItemCollection result = new ItemCollection();
        result.setItemValue("id", bpmnElement.getId());
        result.setType(bpmnElement.getType());
        if (bpmnElement.hasAttribute("name")) {
            result.setItemValue("name", bpmnElement.getAttribute("name"));
        }
        result.setItemValue(OpenBPMNUtil.TASK_ITEM_DOCUMENTATION, bpmnElement.getDocumentation());

        if (bpmnElement instanceof Activity) {
            result.setItemValue("taskID",
                    Long.parseLong(bpmnElement.getExtensionAttribute(OpenBPMNUtil.getNamespace(), "processid")));
            // resolve BoundaryEvents
            resolveBoundaryEvents((Activity) bpmnElement, result);
        }
        if (bpmnElement instanceof Event) {
            result.setItemValue("eventID",
                    Long.parseLong(bpmnElement.getExtensionAttribute(OpenBPMNUtil.getNamespace(), "activityid")));
            // resolve SignalDefinitions and set the adapter.id itemList
            resolveSignalDefinitions((Event) bpmnElement, result);
        }

        // parse imixs extension attributes
        Element extensionElement = bpmnElement.getModel().findChildNodeByName(bpmnElement.getElementNode(),
                BPMNNS.BPMN2, "extensionElements");
        Set<Element> imixsExtensionElements = OpenBPMNUtil.findAllImixsElements(extensionElement, "item");
        // iterate through set and verify the name attribute
        for (Element extensionItem : imixsExtensionElements) {
            String itemName = extensionItem.getAttribute("name");
            if (itemName != null && !itemName.isEmpty()) {
                // found
                List itemValueList = getItemValueList(extensionItem);
                result.setItemValue(itemName, itemValueList);
            }
        }

        // resolve DataObjects
        if (bpmnElement instanceof Event || bpmnElement instanceof Activity) {
            resolveDataObjects(bpmnElement, result);
        }

        // support deprecated item values
        convertDeprecatedItemValues(bpmnElement, result);
        return result;

    }

    /**
     * Resolve Boundary events attached to a Task
     * 
     * 
     * <pre>{@code 
     * <bpmn2:boundaryEvent id="BoundaryEvent_1" name="" attachedToRef="Task_1">
     *       <bpmn2:outgoing>SequenceFlow_8</bpmn2:outgoing>
     *       <bpmn2:timerEventDefinition id="TimerEventDefinition_2">
     *           <bpmn2:timeDuration xsi:type="bpmn2:tFormalExpression" id=
       "FormalExpression_4">1000</bpmn2:timeDuration>
     *       </bpmn2:timerEventDefinition>
     * </bpmn2:boundaryEvent>
     *     }</pre>
     * 
     * @param activity
     * @param result
     */
    private static void resolveBoundaryEvents(Activity activity, ItemCollection result) {

        List<Event> boundaryEvents = activity.getAllBoundaryEvents();
        if (boundaryEvents != null && boundaryEvents.size() > 0) {
            BPMNModel model = activity.getModel();
            Event boundaryEvent = boundaryEvents.get(0);

            // find next imixs event
            BPMNFlowIterator<BPMNElementNode> elementNavigator;
            elementNavigator = new BPMNFlowIterator<BPMNElementNode>(
                    boundaryEvent,
                    node -> ((OpenBPMNUtil.isImixsEventElement(node))));

            if (elementNavigator.hasNext()) {
                BPMNElementNode targetEvent = elementNavigator.next();

                // "boundaryEvent.targetEvent"
                result.setItemValue("boundaryEvent.targetEvent",
                        Long.parseLong(targetEvent.getExtensionAttribute(OpenBPMNUtil.getNamespace(), "activityid")));
                // boundaryEvent.timerEventDefinition.timeDuration
                try {
                    Element timerEventDefinition = boundaryEvent.getChildNode(BPMNNS.BPMN2, "timerEventDefinition");
                    if (timerEventDefinition != null) {
                        Set<Element> elementList = model.findChildNodesByName(timerEventDefinition, BPMNNS.BPMN2,
                                "timeDuration");
                        if (elementList != null && elementList.size() > 0) {
                            Element timerDuration = elementList.iterator().next();
                            if (timerDuration != null) {
                                String duration = timerDuration.getTextContent();
                                result.setItemValue("boundaryEvent.timerEventDefinition.timeDuration",
                                        Integer.parseInt(duration));
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warning("Invalid Boundary Event - missing timeDuration: " + e.getMessage());
                }
            }
        }
    }

    /**
     * This method resolves the optional SignalDefinitions and appends the signal
     * names to the item "adapter.id"
     * 
     * @param event
     * @return
     */
    private static void resolveSignalDefinitions(Event event, ItemCollection entity) {
        BPMNModel model = event.getModel();
        Set<Element> eventDefinitions = event.getEventDefinitionsByType("signalEventDefinition");
        for (Element definition : eventDefinitions) {
            String signalRefID = definition.getAttribute("signalRef");
            Signal signal = model.findSignal(signalRefID);
            if (signal != null) {
                entity.appendItemValueUnique("adapter.id", signal.getName());
            }
        }
    }

    /**
     * This method resolves the DataObjects connected with the given Flow Element.
     * The method adds the attribute "dataObjects" to the given entity
     * 
     * @param event
     * @return
     */
    @SuppressWarnings("unchecked")
    private static void resolveDataObjects(BPMNElementNode elementNode, ItemCollection entity) {

        Set<DataObject> dataObjects = elementNode.getDataObjects();
        // find Data object by name...
        for (DataObject dataObject : dataObjects) {
            List<String> data = new ArrayList<>();
            // get name an documentation
            data.add(dataObject.getName());
            data.add(dataObject.getDocumentation());
            List<List<?>> values = entity.getItemValue("dataObjects");
            values.add(data);
            entity.setItemValue("dataObjects", values);
        }
    }

    /**
     * This helper method returns a value list of a given imixs extension element.
     * If no values exists, than the method returns an empty List
     * 
     * The method also avoids duplicates as this can of course not be handled by the
     * react component.
     * 
     * 
     * @param itemName      - name of the item
     * @param referenceList - optional list of allowed values
     * @return the itemValue list.
     */
    private static List<String> getItemValueList(Element imixsItemElement) {
        List<String> uniqueValueList = new ArrayList<>();

        if (imixsItemElement != null) {
            // now iterate over all item:values and add each value into the list
            // <imixs:value><![CDATA[form_basic]]></imixs:value>
            Set<Element> imixsValueElements = OpenBPMNUtil.findAllImixsElements(imixsItemElement, "value");
            if (imixsValueElements != null) {
                for (Element imixsItemValue : imixsValueElements) {
                    String value = null;
                    // we expect a CDATA, bu we can not be sure
                    Node cdata = findCDATA(imixsItemValue);
                    if (cdata != null) {
                        String cdValue = cdata.getNodeValue();
                        if (cdValue != null) {
                            value = cdValue;
                        }
                    } else {
                        // normal text node
                        value = imixsItemValue.getTextContent();
                    }

                    // avoid duplicates
                    if (value.contains("|")) {
                        String valuePart = value.substring(value.indexOf("|") + 1).trim();
                        if (uniqueValueList.contains(valuePart)) {
                            continue;
                        }
                        uniqueValueList.add(valuePart);
                    } else {
                        if (uniqueValueList.contains(value)) {
                            continue;
                        }
                        uniqueValueList.add(value);
                    }

                }

            }
        }
        return uniqueValueList;
    }

    /**
     * Helper method that finds an optional CDATA node within the current element
     * content.
     * 
     * @param element
     * @return
     */
    private static Node findCDATA(Element element) {
        // search CDATA node
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof CDATASection) {
                return (CDATASection) node;
            }
        }
        return null;
    }

    /**
     * This method converts the new ItemValues to the deprecated item names. This is
     * only for backward compatibility.
     * 
     * @param element
     */
    private static void convertDeprecatedItemValues(BPMNElement bpmnElement, ItemCollection element) {
        element.setItemValue("numActivityid", element.getItemValue("eventID"));
        element.setItemValue("numProcessid", element.getItemValue("taskID"));
        if (bpmnElement instanceof Activity) {
            adaptDeprecatedTaskProperties(element);
        }
        if (bpmnElement instanceof Event) {
            adaptDeprecatedEventProperties(element);
        }
    }

    /**
     * This is a helper method to adapt the old property names into the new. The
     * method also works the other way around so that new imixs-workflow can handle
     * old bpmn files too.
     * 
     * @param currentEntity2
     */
    private static void adaptDeprecatedTaskProperties(ItemCollection taskEntity) {

        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_NAME, "txtname");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_DOCUMENTATION, "rtfdescription");

        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_WORKFLOW_SUMMARY, "txtworkflowsummary");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_WORKFLOW_ABSTRACT, "txtworkflowabstract");

        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_APPLICATION_EDITOR, "txteditorid");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_APPLICATION_ICON, "txtimageurl");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_APPLICATION_TYPE, "txttype");

        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_ACL_OWNER_LIST, "namownershipnames");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_ACL_OWNER_LIST_MAPPING, "keyownershipfields");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_ACL_READACCESS_LIST, "namaddreadaccess");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_ACL_READACCESS_LIST_MAPPING, "keyaddreadfields");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_ACL_WRITEACCESS_LIST, "namaddwriteaccess");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_ACL_WRITEACCESS_LIST_MAPPING, "keyaddwritefields");
        adaptDeprecatedItem(taskEntity, OpenBPMNUtil.TASK_ITEM_ACL_UPDATE, "keyupdateacl");

    }

    /**
     * This is a helper method to adapt the old property names into the new. The
     * method also works the other way around so that new imixs-workflow can handle
     * old bpmn files too.
     * 
     * @param currentEntity2
     */
    private static void adaptDeprecatedEventProperties(ItemCollection eventEntity) {

        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_NAME, "txtname");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_DOCUMENTATION, "rtfdescription");

        // migrate keypublicresult
        if (!eventEntity.hasItem("keypublicresult")) {
            if (!eventEntity.hasItem(OpenBPMNUtil.EVENT_ITEM_WORKFLOW_PUBLIC)) {
                eventEntity.setItemValue(OpenBPMNUtil.EVENT_ITEM_WORKFLOW_PUBLIC, true);
            } else {
                if (!eventEntity.hasItem(OpenBPMNUtil.EVENT_ITEM_WORKFLOW_PUBLIC)) {
                    eventEntity.setItemValue(OpenBPMNUtil.EVENT_ITEM_WORKFLOW_PUBLIC,
                            !"0".equals(eventEntity.getItemValueString("keypublicresult")));
                }
            }
        } else {
            if (!eventEntity.hasItem(OpenBPMNUtil.EVENT_ITEM_WORKFLOW_PUBLIC)) {
                eventEntity.setItemValue(OpenBPMNUtil.EVENT_ITEM_WORKFLOW_PUBLIC,
                        !"0".equals(eventEntity.getItemValueString("keypublicresult")));
            }
        }
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_WORKFLOW_PUBLIC_ACTORS, "keyrestrictedvisibility");

        // acl
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_ACL_OWNER_LIST, "namownershipnames");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_ACL_OWNER_LIST_MAPPING, "keyownershipfields");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_ACL_READACCESS_LIST, "namaddreadaccess");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_ACL_READACCESS_LIST_MAPPING, "keyaddreadfields");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_ACL_WRITEACCESS_LIST, "namaddwriteaccess");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_ACL_WRITEACCESS_LIST_MAPPING, "keyaddwritefields");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_ACL_UPDATE, "keyupdateacl");

        // workflow
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_WORKFLOW_RESULT, "txtactivityresult");

        // history
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_HISTORY_MESSAGE, "rtfresultlog");

        // mail
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_MAIL_SUBJECT, "txtmailsubject");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_MAIL_BODY, "rtfmailbody");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_MAIL_TO_LIST, "nammailreceiver");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_MAIL_TO_LIST_MAPPING, "keymailreceiverfields");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_MAIL_CC_LIST, "nammailreceivercc");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_MAIL_CC_LIST_MAPPING, "keymailreceiverfieldscc");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_MAIL_BCC_LIST, "nammailreceiverbcc");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_MAIL_BCC_LIST_MAPPING, "keymailreceiverfieldsbcc");

        // rule
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_RULE_ENGINE, "txtbusinessruleengine");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_RULE_DEFINITION, "txtbusinessrule");

        // report
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_REPORT_NAME, "txtreportname");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_REPORT_PATH, "txtreportfilepath");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_REPORT_OPTIONS, "txtreportparams");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_REPORT_TARGET, "txtreporttarget");

        // version
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_VERSION_MODE, "keyversion");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_VERSION_EVENT, "numversionactivityid");

        // timer
        if (!eventEntity.hasItem(OpenBPMNUtil.EVENT_ITEM_TIMER_ACTIVE)) {
            eventEntity.setItemValue(OpenBPMNUtil.EVENT_ITEM_TIMER_ACTIVE,
                    Boolean.valueOf("1".equals(eventEntity.getItemValueString("keyscheduledactivity"))));
        }
        if (!eventEntity.hasItem("keyscheduledactivity")) {
            if (eventEntity.getItemValueBoolean(OpenBPMNUtil.EVENT_ITEM_TIMER_ACTIVE)) {
                eventEntity.setItemValue("keyscheduledactivity", "1");
            } else {
                eventEntity.setItemValue("keyscheduledactivity", "0");
            }
        }
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_TIMER_SELECTION, "txtscheduledview");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_TIMER_DELAY, "numactivitydelay");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_TIMER_DELAY_UNIT, "keyactivitydelayunit");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_TIMER_DELAY_BASE, "keyscheduledbaseobject");
        adaptDeprecatedItem(eventEntity, OpenBPMNUtil.EVENT_ITEM_TIMER_DELAY_BASE_PROPERTY, "keytimecomparefield");

    }

    /**
     * Helper method to adopt a old name into a new one
     * 
     * @param taskEntity
     * @param newItemName
     * @param oldItemName
     */
    private static void adaptDeprecatedItem(ItemCollection taskEntity, String newItemName, String oldItemName) {

        // test if old name is provided with a value...
        if (taskEntity.getItemValueString(newItemName).isEmpty()
                && !taskEntity.getItemValueString(oldItemName).isEmpty()) {
            taskEntity.replaceItemValue(newItemName, taskEntity.getItemValue(oldItemName));
        }

        // now we support backward compatibility and add the old name if missing
        if (taskEntity.getItemValueString(oldItemName).isEmpty()) {
            taskEntity.replaceItemValue(oldItemName, taskEntity.getItemValue(newItemName));
        }

    }

}
