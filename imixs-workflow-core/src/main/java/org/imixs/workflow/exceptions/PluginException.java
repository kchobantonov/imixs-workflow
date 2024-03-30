/*  
 *  Imixs-Workflow 
 *  
 *  Copyright (C) 2001-2020 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Project: 
 *      https://www.imixs.org
 *      https://github.com/imixs/imixs-workflow
 *  
 *  Contributors:  
 *      Imixs Software Solutions GmbH - Project Management
 *      Ralph Soika - Software Developer
 */

package org.imixs.workflow.exceptions;

/**
 * A PluginException is thrown by an Imixs-Workflow plugin implementation.
 * 
 * @author rsoika
 * 
 */
public class PluginException extends WorkflowException {

    private static final long serialVersionUID = 1L;
    private java.lang.Object[] params = null;

    public PluginException(String aErrorContext, String aErrorCode, String message) {
        super(aErrorContext, aErrorCode, message);
    }

    public PluginException(String aErrorContext, String aErrorCode, String message, Exception e) {
        super(aErrorContext, aErrorCode, message, e);
    }

    public PluginException(AdapterException e) {
        super(e.getErrorContext(), e.getErrorCode(), e.getMessage(), e);
    }

    public PluginException(String aErrorContext, String aErrorCode, String message, java.lang.Object[] params) {
        super(aErrorContext, aErrorCode, message);
        this.params = params;
    }

    public Object[] getErrorParameters() {
        return params;
    }

    protected void setErrorParameters(java.lang.Object[] aparams) {
        this.params = aparams;
    }

    @Override
    public String formatErrorMessageWithParameters(String message){
        if (params != null && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
                message = message.replace("{" + i + "}", params[i].toString());
            }
        }

        return message;
    }
}
