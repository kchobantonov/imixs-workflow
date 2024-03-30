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

package org.imixs.workflow.jaxrs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.bpmn.BPMNModel;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.ModelService;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import jakarta.ejb.Stateless;
import jakarta.servlet.http.HttpServletRequest;
import java.util.logging.Level;

/**
 * The WorkflowService Handler supports methods to process different kind of
 * request URIs
 * 
 * @author rsoika
 * 
 */
@Path("/model")
@Produces({ MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON,
        MediaType.TEXT_XML })
@Stateless
public class ModelRestService {
    private static final Logger logger = Logger.getLogger(ModelRestService.class.getName());

    static List<String> modelEntityTypes = Arrays.asList("WorkflowEnvironmentEntity", "processentity",
            "activityentity");

    @Inject
    private DocumentService documentService;

    @Inject
    private WorkflowRestService workflowRestService;

    @Inject
    private ModelService modelService;

    @Inject
    private DocumentRestService documentRestService;

    @jakarta.ws.rs.core.Context
    private HttpServletRequest servletRequest;

    @GET
    @Produces({ MediaType.TEXT_HTML })
    public StreamingOutput getModelOverview() {
        return new StreamingOutput() {
            public void write(OutputStream out) throws IOException, WebApplicationException {

                out.write("<html><head>".getBytes());
                out.write("<style>".getBytes());
                out.write("table {padding:0px;width: 75%;margin-left: -2px;margin-right: -2px;}".getBytes());
                out.write(
                        "body,td,select,input,li {font-family: Verdana, Helvetica, Arial, sans-serif;font-size: 13px;}"
                                .getBytes());
                out.write("table th {color: white;background-color: #bbb;text-align: left;font-weight: bold;}"
                        .getBytes());

                out.write("table th,table td {font-size: 12px;}".getBytes());

                out.write("table tr.a {background-color: #ddd;}".getBytes());

                out.write("table tr.b {background-color: #eee;}".getBytes());

                out.write("</style>".getBytes());
                out.write("</head><body>".getBytes());

                out.write("<h1>Imixs-Workflow Model Service</h1>".getBytes());
                out.write("<p>".getBytes());
                printVersionTable(out);
                out.write("</p>".getBytes());
                // footer
                out.write(
                        "<p>See the <a href=\"http://www.imixs.org/doc/restapi/modelservice.html\" target=\"_bank\">Imixs-Workflow REST API</a> for more information.</p>"
                                .getBytes());

                // end
                out.write("</body></html>".getBytes());
            }
        };

    }

    /**
     * This helper method prints the current model information in html format
     */
    private void printVersionTable(OutputStream out) {
        try {
            StringBuffer buffer = new StringBuffer();

            // compute rootContext:
            String rootContext = servletRequest.getContextPath() + servletRequest.getServletPath();

            buffer.append("<table>");
            buffer.append("<tr><th>Version</th><th>Uploaded</th><th>Workflow Groups</th></tr>");
            // append current model version table as a html string
            buffer.append(modelService.modelVersionTableToString(rootContext));

            buffer.append("</table>");
            out.write(buffer.toString().getBytes());
        } catch (ModelException | IOException e) {
            // no opp!
            try {
                out.write("No model definition found.".getBytes());
            } catch (IOException e1) {

                e1.printStackTrace();
            }
        }
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public String getModelXML() {
        List<String> col = null;
        StringBuffer sb = new StringBuffer();
        sb.append("<model>");
        try {
            col = modelService.getVersions();

            for (String aversion : col) {
                sb.append("<version>" + aversion + "</version>");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sb.append("</model>");
        return sb.toString();
    }

    @GET
    @Path("/{version}/tasks/")
    public Response findAllTasks(@PathParam("version") String version, @QueryParam("items") String items,
            @QueryParam("format") String format) {
        List<ItemCollection> result = null;
        try {
            result = modelService.getModel(version).findAllTasks();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return documentRestService.convertResultList(result, items, format);
    }

    @GET
    @Path("/{version}/bpmn")
    public Response getModelFile(@PathParam("version") String version, @Context UriInfo uriInfo) {
        ItemCollection modelEntity = modelService.loadModelEntity(version);
        if (modelEntity != null) {
            return workflowRestService.getWorkItemFile(modelEntity.getUniqueID(), modelEntity.getFileNames().get(0),
                    uriInfo);
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    /**
     * Returns the model definition containing general model information (e.g.
     * $ModelVersion).
     * 
     */
    @GET
    @Path("/{version}/definition")
    public Response getDefiniton(@PathParam("version") String version, @QueryParam("items") String items,
            @QueryParam("format") String format) {
        ItemCollection definition = null;
        try {
            definition = modelService.getModel(version).getDefinition();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return documentRestService.convertResult(definition, items, format);
    }

    @GET
    @Path("/{version}/tasks/{taskid}")
    public Response getTask(@PathParam("version") String version, @PathParam("taskid") int processid,
            @QueryParam("items") String items, @QueryParam("format") String format) {
        ItemCollection task = null;
        try {
            task = modelService.getModel(version).getTask(processid);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return documentRestService.convertResult(task, items, format);
    }

    @GET
    @Path("/{version}/tasks/{taskid}/events")
    public Response findAllEventsByTask(@PathParam("version") String version, @PathParam("taskid") int processid,
            @QueryParam("items") String items, @QueryParam("format") String format) {
        List<ItemCollection> result = null;
        try {
            result = modelService.getModel(version).findAllEventsByTask(processid);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return documentRestService.convertResultList(result, items, format);
    }

    /**
     * Retuns a list of all Start Entities from each workflowgroup
     * 
     * @param version
     * @return
     */
    @GET
    @Path("/{version}/groups")
    public List<String> getGroups(@PathParam("version") String version, @QueryParam("items") String items) {
        List<String> col = null;
        try {
            col = modelService.getModel(version).getGroups();
            return col;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns a list of all Tasks of a specific workflow group.
     * 
     * @param version
     * @return
     */
    @GET
    @Path("/{version}/groups/{group}")
    public Response findTasksByGroup(@PathParam("version") String version, @PathParam("group") String group,
            @QueryParam("items") String items, @QueryParam("format") String format) {
        List<ItemCollection> result = null;
        try {
            result = modelService.getModel(version).findTasksByGroup(group);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return documentRestService.convertResultList(result, items, format);
    }

    @DELETE
    @Path("/{version}")
    public void deleteModel(@PathParam("version") String version) {
        try {
            modelService.deleteModel(version);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * This method consumes a Imixs BPMN model file and updates the corresponding
     * model information.
     * 
     * @param model
     * @return
     */
    @PUT
    @Path("/bpmn")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN })
    public Response putBPMNModel(BPMNModel bpmnmodel) {
        try {
            logger.fine("BPMN Model posted... ");
            modelService.saveModel(bpmnmodel);
        } catch (ModelException e) {
            logger.log(Level.WARNING, "Unable to update model: {0}", e.getMessage());
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }
        logger.fine("putBPMNModel finished! ");
        return Response.status(Response.Status.OK).build();
    }

    /**
     * This method consumes a Imixs BPMN model file and updates the corresponding
     * model information.
     * <p>
     * The filename param is used to store the file in the corresponding bpmn
     * document.
     * 
     * @param model
     * @return
     */
    @PUT
    @Path("/bpmn/{filename}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN })
    public Response putBPMNModel(@PathParam("filename") String filename, BPMNModel bpmnmodel) {
        try {
            logger.fine("BPMN Model posted... ");
            modelService.saveModel(bpmnmodel, filename);
        } catch (ModelException e) {
            logger.log(Level.WARNING, "Unable to update model: {0}", e.getMessage());
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }
        logger.fine("putBPMNModel finished! ");
        return Response.status(Response.Status.OK).build();
    }

    @POST
    @Path("/bpmn")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN })
    public Response postBPMNModel(BPMNModel bpmnmodel) {
        return putBPMNModel(bpmnmodel);
    }

    @POST
    @Path("/bpmn/{filename}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN })
    public Response postBPMNModel(@PathParam("filename") String filename, BPMNModel bpmnmodel) {
        return putBPMNModel(filename, bpmnmodel);
    }

    /**
     * This method updates a Model provided in a EntityCollection object for a
     * provided model version. The Method expects a subresource with a ModelVersion.
     * Next the method updates each Entity object with the property $ModelVersion.
     * An old version will be automatically removed before update.
     * 
     * @param version - $modelversion
     * @param ecol    - model data
     */
    @PUT
    @Path("/{version}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public void putModelByVersion(@PathParam("version") final String _modelVersion, XMLDataCollection ecol) {

        String sModelVersion = _modelVersion;
        XMLDocument entity;
        ItemCollection itemCollection;
        try {
            if (ecol.getDocument().length > 0) {
                /*
                 * first we need to delete the old model if available.
                 */
                if (sModelVersion == null)
                    sModelVersion = "";

                // delete old model if a modelversion is available
                if (!"".equals(sModelVersion))
                    modelService.removeModel(sModelVersion);

                // save new entities into database and update modelversion.....
                for (int i = 0; i < ecol.getDocument().length; i++) {
                    entity = ecol.getDocument()[i];
                    itemCollection = XMLDocumentAdapter.putDocument(entity);
                    // update model version
                    itemCollection.replaceItemValue("$modelVersion", sModelVersion);
                    // save entity
                    documentService.save(itemCollection);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @POST
    @Path("/{version}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public void postModelByVersion(@PathParam("version") String sModelVersion, XMLDataCollection ecol) {
        putModelByVersion(sModelVersion, ecol);
    }

    /**
     * This method updates a Model provided in a EntityCollection object.
     * 
     * The method takes the first entity to get the provided $modelVersion. An old
     * version will be automatically removed before update.
     * 
     * @param ecol
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public void putModel(XMLDataCollection ecol) {
        String sModelVersion = null;
        XMLDocument entity;
        ItemCollection itemCollection;
        try {
            if (ecol.getDocument().length > 0) {
                /*
                 * first we need get model version from first entity
                 */
                entity = ecol.getDocument()[0];
                itemCollection = XMLDocumentAdapter.putDocument(entity);
                sModelVersion = itemCollection.getItemValueString("$ModelVersion");

                putModelByVersion(sModelVersion, ecol);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public void postModel(XMLDataCollection ecol) {
        putModel(ecol);
    }

}
