/*******************************************************************************
 * Licensed partyIdTo the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

package org.ofbiz.party.party;

import javolution.util.FastMap;
import org.ofbiz.base.util.*;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.security.Security;
import org.ofbiz.service.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * Services for Party Relationship maintenance
 */
public class PartyRelationshipServices {

    public static final String module = PartyRelationshipServices.class.getName();
    public static final String resource = "PartyUiLabels";
    public static final String resourceError = "PartyErrorUiLabels";

    /** Creates a PartyRelationshipType
     *@param ctx The DispatchContext that this service is operating in
     *@param context Map containing the input parameters
     *@return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> createPartyRelationshipType(DispatchContext ctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = FastMap.newInstance();
        Delegator delegator = ctx.getDelegator();
        Security security = ctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        ServiceUtil.getPartyIdCheckSecurity(userLogin, security, context, result, "PARTYMGR", "_CREATE");

        if (result.size() > 0)
            return result;

        GenericValue partyRelationshipType = delegator.makeValue("PartyRelationshipType", UtilMisc.toMap("partyRelationshipTypeId", context.get("partyRelationshipTypeId")));

        partyRelationshipType.set("parentTypeId", context.get("parentTypeId"), false);
        partyRelationshipType.set("hasTable", context.get("hasTable"), false);
        partyRelationshipType.set("roleTypeIdValidFrom", context.get("roleTypeIdValidFrom"), false);
        partyRelationshipType.set("roleTypeIdValidTo", context.get("roleTypeIdValidTo"), false);
        partyRelationshipType.set("description", context.get("description"), false);
        partyRelationshipType.set("partyRelationshipName", context.get("partyRelationshipName"), false);

        try {
            if (delegator.findOne(partyRelationshipType.getEntityName(), partyRelationshipType.getPrimaryKey(), false) != null) {
                return ServiceUtil.returnError(UtilProperties.getMessage(resource, 
                        "PartyRelationshipTypeAlreadyExists", locale));
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resource, 
                    "PartyRelationshipTypeReadFailure",
                    UtilMisc.toMap("errorString", e.getMessage()),    locale));
        }

        try {
            partyRelationshipType.create();
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resource, 
                    "PartyRelationshipTypeWriteFailure",
                    UtilMisc.toMap("errorString", e.getMessage()),    locale));
        }

        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    /** Creates and updates a PartyRelationship creating related PartyRoles if needed.
     *  A side of the relationship is checked to maintain history
     *@param ctx The DispatchContext that this service is operating in
     *@param context Map containing the input parameters
     *@return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> createUpdatePartyRelationshipAndRoles(DispatchContext ctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = FastMap.newInstance();
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");

        try {
            List<GenericValue> partyRelationShipList = PartyRelationshipHelper.getActivePartyRelationships(delegator, context);
            if (UtilValidate.isEmpty(partyRelationShipList)) { // If already exists and active nothing to do: keep the current one
                String partyId = (String) context.get("partyId") ;
                String partyIdFrom = (String) context.get("partyIdFrom") ;
                String partyIdTo = (String) context.get("partyIdTo") ;
                String roleTypeIdFrom = (String) context.get("roleTypeIdFrom") ;
                String roleTypeIdTo = (String) context.get("roleTypeIdTo") ;
                String partyRelationshipTypeId = (String) context.get("partyRelationshipTypeId") ;

                // Before creating the partyRelationShip, create the partyRoles if they don't exist
                GenericValue partyToRole = null;
                partyToRole = delegator.findOne("PartyRole", UtilMisc.toMap("partyId", partyIdTo, "roleTypeId", roleTypeIdTo), false);
                if (partyToRole == null) {
                    partyToRole = delegator.makeValue("PartyRole", UtilMisc.toMap("partyId", partyIdTo, "roleTypeId", roleTypeIdTo));
                    partyToRole.create();
                }

                GenericValue partyFromRole= null;
                partyFromRole = delegator.findOne("PartyRole", UtilMisc.toMap("partyId", partyIdFrom, "roleTypeId", roleTypeIdFrom), false);
                if (partyFromRole == null) {
                    partyFromRole = delegator.makeValue("PartyRole", UtilMisc.toMap("partyId", partyIdFrom, "roleTypeId", roleTypeIdFrom));
                    partyFromRole.create();
                }

                // Check if there is already a partyRelationship of that type with another party from the side indicated
                String sideChecked = partyIdFrom.equals(partyId)? "partyIdFrom" : "partyIdTo";
                partyRelationShipList = delegator.findByAnd("PartyRelationship", UtilMisc.toMap(sideChecked, partyId,
                        "roleTypeIdFrom", roleTypeIdFrom,
                        "roleTypeIdTo", roleTypeIdTo,
                        "partyRelationshipTypeId", partyRelationshipTypeId));
                // We consider the last one (in time) as sole active (we try to maintain a unique relationship and keep changes history)
                partyRelationShipList = EntityUtil.filterByDate(partyRelationShipList);
                GenericValue oldPartyRelationShip = EntityUtil.getFirst(partyRelationShipList);
                if (UtilValidate.isNotEmpty(oldPartyRelationShip)) {
                        oldPartyRelationShip.setFields(UtilMisc.toMap("thruDate", UtilDateTime.nowTimestamp())); // Current becomes inactive
                        oldPartyRelationShip.store();
                }
                try {
                    dispatcher.runSync("createPartyRelationship", context); // Create new one
                } catch (GenericServiceException e) {
                    Debug.logWarning(e.getMessage(), module);
                    return ServiceUtil.returnError(UtilProperties.getMessage(resourceError, 
                            "partyrelationshipservices.could_not_create_party_role_write",
                            UtilMisc.toMap("errorString", e.getMessage()), locale));
                }
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError, 
                    "partyrelationshipservices.could_not_create_party_role_write",
                    UtilMisc.toMap("errorString", e.getMessage()), locale));
        }
        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }
}
