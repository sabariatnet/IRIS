package com.temenos.interaction.core.rim;

/*
 * #%L
 * interaction-core
 * %%
 * Copyright (C) 2012 - 2013 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.core.UriInfo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.command.CommandController;
import com.temenos.interaction.core.command.CommandHelper;
import com.temenos.interaction.core.command.GETExceptionCommand;
import com.temenos.interaction.core.command.HttpStatusTypes;
import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionCommand.Result;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.command.MapBasedCommandController;
import com.temenos.interaction.core.command.NoopGETCommand;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityMetadata;
import com.temenos.interaction.core.entity.EntityProperties;
import com.temenos.interaction.core.entity.GenericError;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.Action;
import com.temenos.interaction.core.hypermedia.BeanTransformer;
import com.temenos.interaction.core.hypermedia.CollectionResourceState;
import com.temenos.interaction.core.hypermedia.DynamicResourceState;
import com.temenos.interaction.core.hypermedia.Link;
import com.temenos.interaction.core.hypermedia.ResourceLocator;
import com.temenos.interaction.core.hypermedia.ResourceLocatorProvider;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.hypermedia.expression.Expression;
import com.temenos.interaction.core.hypermedia.expression.ResourceGETExpression;
import com.temenos.interaction.core.hypermedia.expression.ResourceGETExpression.Function;
import com.temenos.interaction.core.hypermedia.expression.SimpleLogicalExpressionEvaluator;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.RESTResource;
import com.temenos.interaction.core.resource.ResourceTypeHelper;
import com.temenos.interaction.core.web.RequestContext;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HTTPHypermediaRIM.class})
public class TestResponseHTTPHypermediaRIM {
	
	class MockEntity {
		String id;
		MockEntity(String id) {
			this.id = id;
		}
		public String getId() {
			return id;
		}
	};

	private @Mock MultivaluedMap<String, String> testInteractionCtxPathParam, 
	    testInteractionCtxQueryParam, uriInfoPathParam, uriInfoQueryParam;
	
	@Before
	public void setup() {
	    MockitoAnnotations.initMocks(this);
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("/baseuri", "/requesturi", null);
        RequestContext.setRequestContext(ctx);
	}

	private List<Action> mockActions() {
		List<Action> actions = new ArrayList<Action>();
		actions.add(new Action("GET", Action.TYPE.VIEW));
		actions.add(new Action("DO", Action.TYPE.ENTRY));
		return actions;
	}

	private List<Action> mockExceptionActions() {
		List<Action> actions = new ArrayList<Action>();
		actions.add(new Action("GETException", Action.TYPE.VIEW));
		return actions;
	}

	private List<Action> mockErrorActions() {
		List<Action> actions = new ArrayList<Action>();
		actions.add(new Action("noop", Action.TYPE.VIEW));
		return actions;
	}
	
	private Metadata createMockMetadata() {
		Metadata metadata = mock(Metadata.class);
		when(metadata.getEntityMetadata(any(String.class))).thenReturn(mock(EntityMetadata.class));
		return metadata;
	}

	/*
	 * This test checks that we receive a 404 'Not Found' if a GET command is not registered.
	 * Every resource must have a GET command, so no command means no resource (404)
	 */
	@Test
	public void testGETCommandNotRegistered() {
		// our empty command controller
		CommandController mockCommandController = mock(CommandController.class);
		when(mockCommandController.fetchCommand("GET")).thenReturn(mock(InteractionCommand.class));

		ResourceState initialState = new ResourceState("entity", "state", new ArrayList<Action>(), "/path");
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
	}

	/*
	 * This test checks that we receive a 405 'Method Not Allowed' if a command is 
	 * not registered for the given method PUT/POST/DELETE.
	 */
	@Test
	public void testPUTCommandNotRegisteredNotAllowedHeader() {
		// our empty command controller
		CommandController mockCommandController = mock(CommandController.class);
		when(mockCommandController.fetchCommand("GET")).thenReturn(mock(InteractionCommand.class));

		ResourceState initialState = new ResourceState("entity", "state", new ArrayList<Action>(), "/path");
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.put(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
		assertEquals(HttpStatusTypes.METHOD_NOT_ALLOWED.getStatusCode(), response.getStatus());
        // as per the http spec, 405 MUST include an Allow header
		List<Object> allowHeader = response.getMetadata().get("Allow");
		assertNotNull(allowHeader);
        assertEquals(1, allowHeader.size());
        HashSet<String> methodsAllowedDetected = new HashSet<String>(Arrays.asList(allowHeader.get(0).toString().split("\\s*,\\s*")));
        HashSet<String> methodsAllowed = new HashSet<String>(Arrays.asList("GET, OPTIONS, HEAD".split("\\s*,\\s*")));
        assertEquals(methodsAllowed, methodsAllowedDetected);
	}

	/*
	 * This test checks that we receive a 405 'Method Not Allowed' if a command is 
	 * not registered for the given method PUT/POST/DELETE.
	 */
	@Test
	public void testPOSTCommandNotRegisteredNotAllowedHeader() {
		// our empty command controller
		CommandController mockCommandController = mock(CommandController.class);
		when(mockCommandController.fetchCommand("GET")).thenReturn(mock(InteractionCommand.class));

		ResourceState initialState = new ResourceState("entity", "state", new ArrayList<Action>(), "/path");
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
		assertEquals(HttpStatusTypes.METHOD_NOT_ALLOWED.getStatusCode(), response.getStatus());
        // as per the http spec, 405 MUST include an Allow header
		List<Object> allowHeader = response.getMetadata().get("Allow");
		assertNotNull(allowHeader);
        assertEquals(1, allowHeader.size());
        HashSet<String> methodsAllowedDetected = new HashSet<String>(Arrays.asList(allowHeader.get(0).toString().split("\\s*,\\s*")));
        HashSet<String> methodsAllowed = new HashSet<String>(Arrays.asList("GET, OPTIONS, HEAD".split("\\s*,\\s*")));
        assertEquals(methodsAllowed, methodsAllowedDetected);
	}

	/*
	 * This test checks that we receive a 405 'Method Not Allowed' if a command is 
	 * not registered for the given method PUT/POST/DELETE.
	 */
	@Test
	public void testDELETECommandNotRegisteredNotAllowedHeader() {
		// our empty command controller
		CommandController mockCommandController = mock(CommandController.class);
		when(mockCommandController.fetchCommand("GET")).thenReturn(mock(InteractionCommand.class));

		ResourceState initialState = new ResourceState("entity", "state", new ArrayList<Action>(), "/path");
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.delete(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		assertEquals(HttpStatusTypes.METHOD_NOT_ALLOWED.getStatusCode(), response.getStatus());
        // as per the http spec, 405 MUST include an Allow header
		List<Object> allowHeader = response.getMetadata().get("Allow");
		assertNotNull(allowHeader);
        assertEquals(1, allowHeader.size());
        HashSet<String> methodsAllowedDetected = new HashSet<String>(Arrays.asList(allowHeader.get(0).toString().split("\\s*,\\s*")));
        HashSet<String> methodsAllowed = new HashSet<String>(Arrays.asList("GET, OPTIONS, HEAD".split("\\s*,\\s*")));
        assertEquals(methodsAllowed, methodsAllowedDetected);
	}

	/*
	 * This test is for a PUT request that returns HttpStatus 204 "No Content"
	 * A PUT command that does not return a new resource will inform the client
	 * that there is no new information to display, continue with the current
	 * view of this resource.
	 */
	@Test
	public void testBuildResponseWithNoContent() throws Exception {
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
		initialState.addTransition(new Transition.Builder().method(HttpMethod.PUT).target(initialState).build());
		/*
		 * construct an InteractionCommand that simply mocks the result of 
		 * storing a resource, with no updated resource for the user agent
		 * to re-display
		 */
		InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) {
				// this is how a command indicates No Content
				ctx.setResource(null);
				return Result.SUCCESS;
			}
		};
		
		// create mock command controller
		CommandController mockCommandController = mock(CommandController.class);
		when(mockCommandController.fetchCommand("GET")).thenReturn(mock(InteractionCommand.class));
		when(mockCommandController.isValidCommand("DO")).thenReturn(true);
		when(mockCommandController.fetchCommand("DO")).thenReturn(mockCommand);

		// RIM with command controller that issues our mock InteractionCommand
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.put(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
		
		// null resource for no content
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 204 http status for no content
		assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}

	/*
	 * This test is for a DELETE request that returns HttpStatus 204 "No Content"
	 * A successful DELETE command does not return a new resource; where a target state
	 * is not found we'll inform the user agent that everything went OK, but there is 
	 * nothing more to display i.e. No Content.
	 */
	@Test
	public void testBuildResponseWith204NoContentNoTransition() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * deleting a resource, with no updated resource for the user agent
		 * to re-display
		 */
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
		initialState.addTransition(new Transition.Builder().method(HttpMethod.DELETE).target(initialState).build());
		InteractionContext testContext = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), 
		        testInteractionCtxPathParam, testInteractionCtxQueryParam, initialState, mock(Metadata.class));
		testContext.setResource(null);
		// mock 'new InteractionContext()' in call to delete
		whenNew(InteractionContext.class).withParameterTypes(UriInfo.class, HttpHeaders.class, MultivaluedMap.class, MultivaluedMap.class, ResourceState.class, Metadata.class)
			.withArguments(any(UriInfo.class), any(HttpHeaders.class), any(MultivaluedMap.class), any(MultivaluedMap.class), any(ResourceState.class), any(Metadata.class)).thenReturn(testContext);
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.delete(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 204 http status for No Content
		assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}

	/*
	 * This test is for a DELETE request that returns HttpStatus 204 "No Content"
	 * A successful DELETE command does not return a new resource; where a target state
	 * is a psuedo final state (effectively no target) we'll inform the user agent
	 * that everything went OK, but there is nothing more to display i.e. No Content.
	 */
	@Test
	public void testBuildResponseWith204NoContent() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * deleting a resource, with no updated resource for the user agent
		 * to re-display
		 */
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
		initialState.addTransition(new Transition.Builder().method("DELETE").target(initialState).build());
		InteractionContext testContext = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), 
		        testInteractionCtxPathParam, testInteractionCtxQueryParam, initialState, mock(Metadata.class));
		testContext.setResource(null);
		// mock 'new InteractionContext()' in call to delete
		whenNew(InteractionContext.class).withParameterTypes(UriInfo.class, HttpHeaders.class, MultivaluedMap.class, MultivaluedMap.class, ResourceState.class, Metadata.class)
			.withArguments(any(UriInfo.class), any(HttpHeaders.class), any(MultivaluedMap.class), any(MultivaluedMap.class), any(ResourceState.class), any(Metadata.class)).thenReturn(testContext);
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.delete(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 204 http status for No Content
		assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}

	/*
	 * This test is for a DELETE request that returns HttpStatus 205 "Reset Content"
	 * A successful DELETE command does not return a new resource and should inform
	 * the user agent to refresh the current view.
	 */
	@Test
	public void testBuildResponseWith205ContentReset() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * deleting a resource, with no updated resource for the user agent
		 * to re-display
		 */
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
		ResourceState deletedState = new ResourceState(initialState, "deleted", mockActions());
		initialState.addTransition(new Transition.Builder().method("DELETE").target(deletedState).build());
		deletedState.addTransition(new Transition.Builder().flags(Transition.REDIRECT).target(initialState).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.delete(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 205 http status for Reset Content
		assertEquals(HttpStatusTypes.RESET_CONTENT.getStatusCode(), response.getStatus());
	}

	/*
	 * This test is for a DELETE request that returns HttpStatus 205 "Reset Content"
	 * A successful DELETE command does not return a new resource and should inform
	 * the user agent to refresh the current view if the target is the same as the source.
	 */
	@Test
	public void testBuildResponseWith205ContentResetDifferentResource() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * deleting a resource, with no updated resource for the user agent
		 * to re-display
		 */
		CollectionResourceState initialState = new CollectionResourceState("entity", "state", mockActions(), "/entities");
		ResourceState existsState = new ResourceState(initialState, "exists", mockActions(), "/123");
		ResourceState deletedState = new ResourceState(existsState, "deleted", mockActions());
		initialState.addTransition(new Transition.Builder().flags(Transition.FOR_EACH).method("GET").target(existsState).build());
		initialState.addTransition(new Transition.Builder().method("DELETE").target(deletedState).build());
		existsState.addTransition(new Transition.Builder().method("DELETE").target(deletedState).build());
		// the auto transition
		deletedState.addTransition(new Transition.Builder().flags(Transition.REDIRECT).target(initialState).build());
		
		InteractionContext testContext = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), 
		        testInteractionCtxPathParam, testInteractionCtxQueryParam, initialState, mock(Metadata.class));
		testContext.setResource(null);
		// mock 'new InteractionContext()' in call to delete
		whenNew(InteractionContext.class).withParameterTypes(UriInfo.class, HttpHeaders.class, MultivaluedMap.class, MultivaluedMap.class, ResourceState.class, Metadata.class)
			.withArguments(any(UriInfo.class), any(HttpHeaders.class), any(MultivaluedMap.class), any(MultivaluedMap.class), any(ResourceState.class), any(Metadata.class)).thenReturn(testContext);
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Collection<ResourceInteractionModel> children = rim.getChildren();
		// find the resource interaction model for the entity item
		HTTPHypermediaRIM itemRIM = null;
		for (ResourceInteractionModel r : children) {
			if (r.getResourcePath().equals("/entities/123")) {
				itemRIM = (HTTPHypermediaRIM) r;
			}
		}
		// mock the Link header
		HttpHeaders mockHeaders = mock(HttpHeaders.class);
		List<String> links = new ArrayList<String>();
		links.add("</path>; rel=\"entity.state>DELETE>entity.deleted\"");
		when(mockHeaders.getRequestHeader("Link")).thenReturn(links);
		Response response = itemRIM.delete(mockHeaders, "id", mockEmptyUriInfo());
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 205 http status for Reset Content
		assertEquals(HttpStatusTypes.RESET_CONTENT.getStatusCode(), response.getStatus());
	}

	/*
	 * This test is for a DELETE request that supplies a custom link relation
	 * via the Link header.  See (see rfc5988)
	 * When a user agent follows a link it is able to supply the link relations
	 * given to it by the server for that link.  This provides the server with
	 * some information about which link the client followed, and therefore
	 * what state/links to show them next.
	 */
	@Test
	public void testBuildResponseWith303SeeOtherDELETESameEntity() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * deleting a resource, with no updated resource for the user agent
		 * to re-display
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState existsState = new ResourceState("toaster", "exists", mockActions(), "/machines/toaster");
		ResourceState cookingState = new ResourceState(existsState, "cooking", mockActions(), "/cooking");
		ResourceState idleState = new ResourceState(cookingState, "idle", mockActions());
		
		// view the toaster if it exists (could show time remaining if cooking)
		initialState.addTransition(new Transition.Builder().method("GET").target(existsState).build());
		// start cooking the toast
		existsState.addTransition(new Transition.Builder().method("PUT").target(cookingState).build());
		// stop the toast cooking
		cookingState.addTransition(new Transition.Builder().method("DELETE").target(idleState).build());
		idleState.addTransition(new Transition.Builder().flags(Transition.REDIRECT).target(existsState).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Collection<ResourceInteractionModel> children = rim.getChildren();
		// find the resource interaction model for the 'cooking' state
		HTTPHypermediaRIM cookingStateRIM = null;
		for (ResourceInteractionModel r : children) {
			if (r.getResourcePath().equals("/machines/toaster/cooking")) {
				cookingStateRIM = (HTTPHypermediaRIM) r;
			}
		}
		// mock the Link header
		HttpHeaders mockHeaders = mock(HttpHeaders.class);
		List<String> links = new ArrayList<String>();
		links.add("</path>; rel=\"toaster.cooking>toaster.idle\"");
		when(mockHeaders.getRequestHeader("Link")).thenReturn(links);
		Response response = cookingStateRIM.delete(mockHeaders, "id", mockEmptyUriInfo());
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 303 "See Other" instructs user agent to fetch another resource as specified by the 'Location' header
		assertEquals(Status.SEE_OTHER.getStatusCode(), response.getStatus());
		List<Object> locationHeader = response.getMetadata().get("Location");
		assertNotNull(locationHeader);
        assertEquals(1, locationHeader.size());
        assertEquals("/baseuri/machines/toaster", locationHeader.get(0));
	}

	/*
	 * This test is for a GET request to a resource that defines an auto transition;
	 * we expect to receive 303 'See Other'.
	 */
	@Test
	public void testBuildResponseWith303SeeOtherGET() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * a GET to a resource, with an auto transition
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState existsState = new ResourceState("toaster", "exists", mockActions(), "/machines/toaster");
		ResourceState existsElsewhereState = new ResourceState("toaster", "existsOther", mockActions(), "/machines/toaster2");
		
		// view the toaster if it exists (could show time remaining if cooking)
		initialState.addTransition(new Transition.Builder().method("GET").target(existsState).build());
		existsState.addTransition(new Transition.Builder().flags(Transition.REDIRECT).target(existsElsewhereState).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Collection<ResourceInteractionModel> children = rim.getChildren();
		// find the resource interaction model for the 'exists' state
		HTTPHypermediaRIM existsStateRIM = null;
		for (ResourceInteractionModel r : children) {
			if (r.getResourcePath().equals("/machines/toaster")) {
				existsStateRIM = (HTTPHypermediaRIM) r;
			}
		}
		// mock the Link header
		Response response = existsStateRIM.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 303 "See Other" instructs user agent to fetch another resource as specified by the 'Location' header
		assertEquals(Status.SEE_OTHER.getStatusCode(), response.getStatus());
		List<Object> locationHeader = response.getMetadata().get("Location");
		assertNotNull(locationHeader);
        assertEquals(1, locationHeader.size());
        assertEquals("/baseuri/machines/toaster2", locationHeader.get(0));
	}

	/*
	 * Same as testBuildResponseWith303SeeOtherGET with parameters
	 */
	@Test
	public void testBuildResponseWith303SeeOtherGETWithParameters() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * a GET to a resource, with an auto transition
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState existsState = new ResourceState("toaster", "exists", mockActions(), "/machines/toaster");
		ResourceState existsElsewhereState = new ResourceState("toaster", "existsOther", mockActions(), "/machines/toaster/{id}");
		
		// view the toaster if it exists (could show time remaining if cooking)
		initialState.addTransition(new Transition.Builder().method("GET").target(existsState).build());
		existsState.addTransition(new Transition.Builder().flags(Transition.REDIRECT).target(existsElsewhereState).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Collection<ResourceInteractionModel> children = rim.getChildren();
		// find the resource interaction model for the 'exists' state
		HTTPHypermediaRIM existsStateRIM = null;
		for (ResourceInteractionModel r : children) {
			if (r.getResourcePath().equals("/machines/toaster")) {
				existsStateRIM = (HTTPHypermediaRIM) r;
			}
		}
		// mock the Link header
		MultivaluedMap<String, String> mockPathParameters = new MultivaluedMapImpl<String>();
		mockPathParameters.add("id", "2");
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mockPathParameters);
		when(uriInfo.getQueryParameters(false)).thenReturn(testInteractionCtxQueryParam);
		Response response = existsStateRIM.get(mock(HttpHeaders.class), "id", uriInfo);
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 303 "See Other" instructs user agent to fetch another resource as specified by the 'Location' header
		assertEquals(Status.SEE_OTHER.getStatusCode(), response.getStatus());
		List<Object> locationHeader = response.getMetadata().get("Location");
		assertNotNull(locationHeader);
        assertEquals(1, locationHeader.size());
        assertEquals("/baseuri/machines/toaster/2", locationHeader.get(0));
	}

	/*
	 * Same as testBuildResponseWith303SeeOtherGET with query parameters
	 */
	@Test
	public void testBuildResponseWith303SeeOtherGETWithQueryParameters() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * a GET to a resource, with an auto transition
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState existsState = new ResourceState("toaster", "exists", mockActions(), "/machines/toaster");
		ResourceState existsElsewhereState = new ResourceState("toaster", "existsOther", mockActions(), "/machines/toaster/{id}");
		
		// view the toaster if it exists (could show time remaining if cooking)
		initialState.addTransition(new Transition.Builder().method("GET").target(existsState).build());
		Map<String,String> uriParameters = new HashMap<String,String>();
		uriParameters.put("test", "{test}");
		existsState.addTransition(new Transition.Builder()
			.flags(Transition.REDIRECT)
			.target(existsElsewhereState)
			.uriParameters(uriParameters)
			.build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Collection<ResourceInteractionModel> children = rim.getChildren();
		// find the resource interaction model for the 'exists' state
		HTTPHypermediaRIM existsStateRIM = null;
		for (ResourceInteractionModel r : children) {
			if (r.getResourcePath().equals("/machines/toaster")) {
				existsStateRIM = (HTTPHypermediaRIM) r;
			}
		}
		// mock the Link header
		MultivaluedMap<String, String> mockPathParameters = new MultivaluedMapImpl<String>();
		mockPathParameters.add("id", "2");
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mockPathParameters);
		MultivaluedMap<String, String> mockQueryParameters = new MultivaluedMapImpl<String>();
		mockQueryParameters.add("test", "123");
		when(uriInfo.getQueryParameters(false)).thenReturn(mockQueryParameters);
		Response response = existsStateRIM.get(mock(HttpHeaders.class), "id", uriInfo);
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 303 "See Other" instructs user agent to fetch another resource as specified by the 'Location' header
		assertEquals(Status.SEE_OTHER.getStatusCode(), response.getStatus());
		List<Object> locationHeader = response.getMetadata().get("Location");
		assertNotNull(locationHeader);
        assertEquals(1, locationHeader.size());
        assertEquals("/baseuri/machines/toaster/2?test=123", locationHeader.get(0));
	}

	/*
	 * Same as testBuildResponseWith303SeeOtherGET with query parameters that replace a token
	 */
	@Test
	public void testBuildResponseWith303SeeOtherGETWithTokenReplaceQueryParameters() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * a GET to a resource, with an auto transition
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState existsState = new ResourceState("toaster", "exists", mockActions(), "/machines/toaster");
		ResourceState existsElsewhereState = new ResourceState("toaster", "existsOther", mockActions(), "/machines/toaster/{id}");
		
		// view the toaster if it exists (could show time remaining if cooking)
		initialState.addTransition(new Transition.Builder().method("GET").target(existsState).build());
		existsState.addTransition(new Transition.Builder().flags(Transition.REDIRECT).target(existsElsewhereState).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Collection<ResourceInteractionModel> children = rim.getChildren();
		// find the resource interaction model for the 'exists' state
		HTTPHypermediaRIM existsStateRIM = null;
		for (ResourceInteractionModel r : children) {
			if (r.getResourcePath().equals("/machines/toaster")) {
				existsStateRIM = (HTTPHypermediaRIM) r;
			}
		}
		// mock the Link header
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getPathParameters(anyBoolean())).thenReturn(new MultivaluedMapImpl<String>());
		MultivaluedMap<String, String> mockQueryParameters = new MultivaluedMapImpl<String>();
		mockQueryParameters.add("id", "123");
		when(uriInfo.getQueryParameters(false)).thenReturn(mockQueryParameters);
		Response response = existsStateRIM.get(mock(HttpHeaders.class), "id", uriInfo);
		
		// null resource
		RESTResource resource = (RESTResource) response.getEntity();
		assertNull(resource);
		// 303 "See Other" instructs user agent to fetch another resource as specified by the 'Location' header
		assertEquals(Status.SEE_OTHER.getStatusCode(), response.getStatus());
		List<Object> locationHeader = response.getMetadata().get("Location");
		assertNotNull(locationHeader);
        assertEquals(1, locationHeader.size());
        assertEquals("/baseuri/machines/toaster/123", locationHeader.get(0));
	}

	/*
	 * This test is for a POST request that creates a new resource.
	 */
	@Test
	public void testBuildResponseWith201Created() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * creating a resource
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState createPsuedoState = new ResourceState(initialState, "create", mockActions());
		ResourceState individualMachine = new ResourceState(initialState, "machine", mockActions(), "/{id}");
		
		// create new machine
		initialState.addTransition(new Transition.Builder().method("POST").target(createPsuedoState).build());
		// an auto transition to the new resource
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		uriLinkageMap.put("id", "{id}");
		createPsuedoState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(individualMachine).uriParameters(uriLinkageMap).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState, new BeanTransformer()), createMockMetadata());
		Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mockEntityResourceWithId("123"));

		// the response entity must not be null and must be an instance of/subtype RESTResource} 
        assertNotNull(GenericEntity.class.isAssignableFrom(response.getEntity().getClass()));
        GenericEntity<?> responseEntity = (GenericEntity<?>)response.getEntity();
        assertTrue(RESTResource.class.isAssignableFrom(responseEntity.getEntity().getClass()));
		/*
		 *  201 "Created" informs the user agent that 'the request has been fulfilled and resulted 
		 *  in a new resource being created'.  It can be accessed by a GET to the resource specified 
		 *  by the 'Location' header
		 */
		assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
		List<Object> locationHeader = response.getMetadata().get("Location");
		assertNotNull(locationHeader);
        assertEquals(1, locationHeader.size());
        assertEquals("/baseuri/machines/123", locationHeader.get(0));
	}

	/*
	 * This test is for a POST request that creates a new resource, and returns
	 * the links for the resource we auto transition to.
	 */
	@Test
	public void testPOSTwithAutoTransition() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * creating a resource
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState createPsuedoState = new ResourceState(initialState, "create", mockActions());
		ResourceState approvePsuedoState = new ResourceState(initialState, "approve", mockActions());
		ResourceState individualMachine = new ResourceState(initialState, "machine", mockActions(), "/{id}");
		individualMachine.addTransition(new Transition.Builder().method("GET").target(initialState).build());
		
		// create new machine
		initialState.addTransition(new Transition.Builder().method("POST").target(createPsuedoState).build());
		
		// The state should transition from create -> approve -> machine; this tests multi state auto transitions  
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		uriLinkageMap.put("id", "{id}");		
		createPsuedoState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(approvePsuedoState).uriParameters(uriLinkageMap).build());
		approvePsuedoState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(individualMachine).uriParameters(uriLinkageMap).build());
				
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState, new BeanTransformer()), createMockMetadata());
		Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mockEntityResourceWithId("123"));

		assertEquals(Status.CREATED.getStatusCode(), response.getStatus());

		// the response entity must not be null and must be an instance of/subtype RESTResource} 
        assertNotNull(GenericEntity.class.isAssignableFrom(response.getEntity().getClass()));
        GenericEntity<?> responseEntity = (GenericEntity<?>)response.getEntity();
        assertTrue(RESTResource.class.isAssignableFrom(responseEntity.getEntity().getClass()));
		/*
		 *  Assert the links in the response match the target resource
		 */
		EntityResource<?> createdResource = (EntityResource<?>) ((GenericEntity<?>)response.getEntity()).getEntity();
		List<Link> links = new ArrayList<Link>(createdResource.getLinks());
		assertEquals(2, links.size());
		assertEquals("machine", links.get(0).getTitle());
		assertEquals("/baseuri/machines/123", links.get(0).getHref());
		assertEquals("initial", links.get(1).getTitle());
		assertEquals("/baseuri/machines", links.get(1).getHref());
	}

	/*
	 * This test is for a POST request that creates a new resource, and has
	 * an auto transition to a resource that does not exist
	 */
	@Test
	public void testPOSTwithAutoTransitionToNonExistentResource() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * creating a resource
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/door");
		ResourceState createPsuedoState = new ResourceState(initialState, "hallway", mockActions());
		ResourceState individualMachine = new ResourceState(initialState, "kitchen", mockActions(), "/{id}");
		individualMachine.addTransition(new Transition.Builder().method("GET").target(initialState).build());
		
		// create new machine
		initialState.addTransition(new Transition.Builder().method("POST").target(createPsuedoState).build());
		// an auto transition to the new resource
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		uriLinkageMap.put("id", "{id}");
		createPsuedoState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(individualMachine).uriParameters(uriLinkageMap).build());
		
		// RIM with command controller that issues commands that return SUCCESS for 'DO' action and FAILURE for 'GET' action (see mockActions())
		Map<String,InteractionCommand> commands = new HashMap<String,InteractionCommand>();
		commands.put("DO", mockCommand_SUCCESS());
		commands.put("GET", mockCommand_FAILURE());
		CommandController commandController = new MapBasedCommandController(commands);
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, new ResourceStateMachine(initialState, new BeanTransformer()), createMockMetadata());
		Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mockEntityResourceWithId("123"));

		assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());

		// the response entity must not be null and must be an instance of/subtype RESTResource} 
        assertNotNull(GenericEntity.class.isAssignableFrom(response.getEntity().getClass()));
        GenericEntity<?> responseEntity = (GenericEntity<?>)response.getEntity();
        assertTrue(RESTResource.class.isAssignableFrom(responseEntity.getEntity().getClass()));
	}
	
	/**
	 * State test for a conditional autotransition to a dynamic resource that does not exist. 
	 */
	@Test
	public void testPOSTWithConditionalAutoTransitionToNonExistentDynamicResource(){
        //given {a graph of resource states comprising of an initial state, 
	    //a middle state and a dynamic final state that doesn't exist}
	    ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/door");
        final ResourceState middleState = new ResourceState(initialState, "hallway", mockActions());
        ResourceState targetState = new DynamicResourceState("garage", "theGarage", "mockLocator", "abcd");
        ResourceLocatorProvider locatorProvider = mock(ResourceLocatorProvider.class);
        ResourceLocator locator = mock(ResourceLocator.class);
        when(locator.resolve(anyVararg())).thenReturn(new ResourceState("missing", "missing", mockActions(), "/missingState"));
        when(locatorProvider.get(eq("mockLocator"))).thenReturn(locator);
        //create autotransition
        middleState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(targetState).evaluation(new ResourceGETExpression(middleState, Function.OK)).build());
        initialState.addTransition(new Transition.Builder().method("POST").target(middleState).evaluation(new ResourceGETExpression(middleState, Function.OK)).build());
        // an auto transition to the new resource
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("id", "{id}");
        Expression notEval = mock(Expression.class);
        when(notEval.evaluate(any(HTTPHypermediaRIM.class), any(InteractionContext.class), Mockito.<EntityResource<?>>any())).thenReturn(false);
        
        // RIM with command controller that issues commands that return SUCCESS for 'DO' action and FAILURE for 'GET' action (see mockActions())
        Map<String,InteractionCommand> commands = new HashMap<String,InteractionCommand>();
        commands.put("DO", mockCommand_SUCCESS());
        commands.put("GET", mockCommand_FAILURE());
        CommandController commandController = new MapBasedCommandController(commands);
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, new ResourceStateMachine(initialState, new BeanTransformer(), locatorProvider), createMockMetadata(), locatorProvider);
        
        //when {the post method is invoked}
        Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mockEntityResourceWithId("123"));
        
        //then {the response must be HTTP 201 Created}
        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
        
        // and {the response entity must not be null and must be an instance of/subtype RESTResource} 
        assertNotNull(GenericEntity.class.isAssignableFrom(response.getEntity().getClass()));
        GenericEntity<?> responseEntity = (GenericEntity<?>)response.getEntity();
        assertTrue(RESTResource.class.isAssignableFrom(responseEntity.getEntity().getClass()));
	}

	/*
	 * This test is for a POST request that creates a new resource, and uses
	 * linkage parameters to get the resource we transition too
	 */
	@Test
	public void testPOSTwithAutoTransitionLinkageParameters() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * creating a resource
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState createPsuedoState = new ResourceState(initialState, "create", mockActions());
		ResourceState individualMachine = new ResourceState(initialState, "machine", mockActions(), "/{test}");
		
		// create new machine
		initialState.addTransition(new Transition.Builder().method("POST").target(createPsuedoState).build());
		// an auto transition with parameters to the new resource
		Map<String, String> linkageMap = new HashMap<String, String>();
		linkageMap.put("test", "{id}");
		createPsuedoState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(individualMachine).uriParameters(linkageMap).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState, new BeanTransformer()), createMockMetadata());
		Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mockEntityResourceWithId("123"));

		// the response entity must not be null and must be an instance of/subtype RESTResource} 
        assertNotNull(GenericEntity.class.isAssignableFrom(response.getEntity().getClass()));
        GenericEntity<?> responseEntity = (GenericEntity<?>)response.getEntity();
        assertTrue(RESTResource.class.isAssignableFrom(responseEntity.getEntity().getClass()));
		/*
		 *  Assert the links in the response match the target resource
		 */
		EntityResource<?> createdResource = (EntityResource<?>) ((GenericEntity<?>)response.getEntity()).getEntity();
		List<Link> links = new ArrayList<Link>(createdResource.getLinks());
		assertEquals(1, links.size());
		assertEquals("/baseuri/machines/123", links.get(0).getHref());
	}

	/*
	 * This test is for a GET request that uses a query linkage parameters to get the 
	 * resource we transition too.
	 */
	@Test
	public void testGETwithAutoTransitionQueryLinkageParameters() throws Exception {
		List<Action> actions = new ArrayList<Action>();
		actions.add(new Action("GET", Action.TYPE.VIEW));
		ResourceState initialState = new ResourceState("home", "initial", actions, "/machines");
		// decide whether to go to 'machine' or 'initial'
		ResourceState conditionPsuedoState = new ResourceState("home", "condition", actions, "/machines/condition");
		ResourceState individualMachine = new ResourceState("home", "machine", actions, "/machines/{test}");
		
		// create new machine
		initialState.addTransition(new Transition.Builder().method("GET").target(conditionPsuedoState).build());
		// an auto transition with parameters to the new resource
		Map<String, String> linkageMap = new HashMap<String, String>();
		linkageMap.put("test", "{mytestparam}");
		conditionPsuedoState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(individualMachine).uriParameters(linkageMap).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		Map<String,InteractionCommand> commands = new HashMap<String,InteractionCommand>();
		commands.put("DO", mockCommand_SUCCESS());  // not used
		commands.put("GET", new InteractionCommand() {

			@Override
			public Result execute(InteractionContext ctx)
					throws InteractionException {
				ctx.setResource(new EntityResource<String>(""));
				return Result.SUCCESS;
			}
			
		});
		CommandController commandController = new MapBasedCommandController(commands);
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, new ResourceStateMachine(initialState, new BeanTransformer()), createMockMetadata());
		Collection<ResourceInteractionModel> children = rim.getChildren();
		// find the resource interaction model for the 'exists' state
		HTTPHypermediaRIM conditionStateRIM = null;
		for (ResourceInteractionModel r : children) {
			if (r.getResourcePath().equals("/machines/condition")) {
				conditionStateRIM = (HTTPHypermediaRIM) r;
			}
		}
		
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getPathParameters(anyBoolean())).thenReturn(new MultivaluedMapImpl<String>());
		MultivaluedMap<String, String> queryParameters = new MultivaluedMapImpl<String>();
		queryParameters.add("mytestparam", "123");
		when(uriInfo.getQueryParameters(false)).thenReturn(queryParameters);
		Response response = conditionStateRIM.get(mock(HttpHeaders.class), "id", uriInfo);

		// the response entity must not be null and must be an instance of/subtype RESTResource} 
        assertNotNull(GenericEntity.class.isAssignableFrom(response.getEntity().getClass()));
        GenericEntity<?> responseEntity = (GenericEntity<?>)response.getEntity();
        assertTrue(RESTResource.class.isAssignableFrom(responseEntity.getEntity().getClass()));		/*
		 *  Assert the links in the response match the target resource
		 */
		EntityResource<?> createdResource = (EntityResource<?>) ((GenericEntity<?>)response.getEntity()).getEntity();
		List<Link> links = new ArrayList<Link>(createdResource.getLinks());
		assertEquals(1, links.size());
		assertEquals("/baseuri/machines/123", links.get(0).getHref());
	}

	private EntityResource<Object> mockEntityResourceWithId(final String id) {
		return new EntityResource<Object>(new MockEntity(id));
	}
	
	@Test
	public void testBuildResponseWithLinks() throws Exception {
		// construct an InteractionContext that simply mocks the result of loading a resource
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
		InteractionContext testContext = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), 
		        testInteractionCtxPathParam, testInteractionCtxQueryParam, initialState, mock(Metadata.class));
		testContext.setResource(new EntityResource<Object>(null));
		// mock 'new InteractionContext()' in call to get
		whenNew(InteractionContext.class).withParameterTypes(UriInfo.class, HttpHeaders.class, MultivaluedMap.class, MultivaluedMap.class, ResourceState.class, Metadata.class)
			.withArguments(any(UriInfo.class), any(HttpHeaders.class), any(MultivaluedMap.class), any(MultivaluedMap.class), any(ResourceState.class), any(Metadata.class)).thenReturn(testContext);
		
		List<Link> links = new ArrayList<Link>();
		links.add(new Link("id", "self", "href", null, null));
		
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		
		RESTResource resourceWithLinks = (RESTResource) ((GenericEntity<?>)response.getEntity()).getEntity();
		assertNotNull(resourceWithLinks.getLinks());
		assertFalse(resourceWithLinks.getLinks().isEmpty());
		assertEquals(1, resourceWithLinks.getLinks().size());
		Link link = (Link) resourceWithLinks.getLinks().toArray()[0];
		assertEquals("self", link.getRel());
	}

	@Test
	public void testBuildResponseWithDynamicLinkParams() throws Exception {
		// construct an InteractionContext that simply mocks the result of loading a resource
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");

        ResourceState b =  new ResourceState(initialState, "b", new ArrayList<Action>());
        Transition t = new Transition.Builder().source(initialState).method("GET").target(b).build();
        initialState.addTransition(t);

		InteractionContext testContext = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), 
		        testInteractionCtxPathParam, testInteractionCtxQueryParam, initialState, mock(Metadata.class));
		
		MultivaluedMap<String, String> outQueryParameters = testContext.getOutQueryParameters();		
		outQueryParameters.add("penguin", "emperor");
		
		testContext.setResource(new EntityResource<Object>(null));
		// mock 'new InteractionContext()' in call to get
		whenNew(InteractionContext.class).withParameterTypes(UriInfo.class, HttpHeaders.class, MultivaluedMap.class, MultivaluedMap.class, ResourceState.class, Metadata.class)
			.withArguments(any(UriInfo.class), any(HttpHeaders.class), any(MultivaluedMap.class), any(MultivaluedMap.class), any(ResourceState.class), any(Metadata.class)).thenReturn(testContext);
		
		List<Link> links = new ArrayList<Link>();
		links.add(new Link("id", "self", "href", null, null));
		
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		
		RESTResource resourceWithLinks = (RESTResource) ((GenericEntity<?>)response.getEntity()).getEntity();
		assertNotNull(resourceWithLinks.getLinks());
		assertFalse(resourceWithLinks.getLinks().isEmpty());
		assertEquals(2, resourceWithLinks.getLinks().size());
		Link link = (Link) resourceWithLinks.getLinks().toArray()[0];
		assertEquals("self", link.getRel());
		
		link = (Link)resourceWithLinks.getLinks().toArray()[1];
		assertEquals("item", link.getRel());
		assertEquals("/baseuri/path?penguin=emperor", link.getHref());
	}
	
    @Test
    public void testBuildResponseWithMultipleDynamicLinkParams() throws Exception {
        // construct an InteractionContext that simply mocks the result of loading a resource
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");

        ResourceState b =  new ResourceState(initialState, "b", new ArrayList<Action>());
        Transition t = new Transition.Builder().source(initialState).method("GET").target(b).build();
        initialState.addTransition(t);

        InteractionContext testContext = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), 
                testInteractionCtxPathParam, testInteractionCtxQueryParam, initialState, mock(Metadata.class));
        
        MultivaluedMap<String, String> outQueryParameters = testContext.getOutQueryParameters();        
        outQueryParameters.add("penguin", "emperor");
        outQueryParameters.add("crispbread", "coconut");
        outQueryParameters.add("penguin", "black");
        
        testContext.setResource(new EntityResource<Object>(null));
        // mock 'new InteractionContext()' in call to get
        whenNew(InteractionContext.class).withParameterTypes(UriInfo.class, HttpHeaders.class, MultivaluedMap.class, MultivaluedMap.class, ResourceState.class, Metadata.class)
            .withArguments(any(UriInfo.class), any(HttpHeaders.class), any(MultivaluedMap.class), any(MultivaluedMap.class), any(ResourceState.class), any(Metadata.class)).thenReturn(testContext);
        
        List<Link> links = new ArrayList<Link>();
        links.add(new Link("id", "self", "href", null, null));
        
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
        Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
        
        RESTResource resourceWithLinks = (RESTResource) ((GenericEntity<?>)response.getEntity()).getEntity();
        assertNotNull(resourceWithLinks.getLinks());
        assertFalse(resourceWithLinks.getLinks().isEmpty());
        assertEquals(2, resourceWithLinks.getLinks().size());
        Link link = (Link) resourceWithLinks.getLinks().toArray()[0];
        assertEquals("self", link.getRel());
        
        link = (Link)resourceWithLinks.getLinks().toArray()[1];
        assertEquals("item", link.getRel());
        assertEquals("/baseuri/path?penguin=emperor&penguin=black&crispbread=coconut", link.getHref());
    }
	
	@Test
	public void testBuildResponseEntityName() throws Exception {
		// construct an InteractionContext that simply mocks the result of loading a resource
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
		InteractionContext testContext = new InteractionContext(mock(UriInfo.class), null, 
		        testInteractionCtxPathParam, testInteractionCtxQueryParam, initialState, mock(Metadata.class));
		testContext.setResource(new EntityResource<Object>(null));
		// mock 'new InteractionContext()' in call to get
		whenNew(InteractionContext.class).withParameterTypes(UriInfo.class, HttpHeaders.class, MultivaluedMap.class, MultivaluedMap.class, ResourceState.class, Metadata.class)
			.withArguments(any(UriInfo.class), any(HttpHeaders.class), any(MultivaluedMap.class), any(MultivaluedMap.class), any(ResourceState.class), any(Metadata.class)).thenReturn(testContext);
		
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		
		RESTResource resource = (RESTResource) ((GenericEntity<?>)response.getEntity()).getEntity();
		assertNotNull(resource.getEntityName());
		assertEquals("entity", resource.getEntityName());
	}

	private UriInfo mockEmptyUriInfo() {
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getPathParameters(anyBoolean())).thenReturn(uriInfoPathParam);
		when(uriInfo.getQueryParameters(false)).thenReturn(uriInfoQueryParam);
		return uriInfo;
	}
	
	private CommandController mockNoopCommandController() {
		// make sure command execution does nothing
		CommandController commandController = mock(CommandController.class);
		InteractionCommand testCommand = mockCommand_SUCCESS();
		when(commandController.isValidCommand(anyString())).thenReturn(true);
		when(commandController.fetchCommand(anyString())).thenReturn(testCommand);
		return commandController;
	}

	private InteractionCommand mockCommand_SUCCESS() {
		InteractionCommand mockCommand = mock(InteractionCommand.class);
		try {
			when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.SUCCESS);
		} catch(InteractionException ie) {
			Assert.fail(ie.getMessage());
		}
		return mockCommand;
	}

	private InteractionCommand mockCommand_FAILURE() {
		InteractionCommand mockCommand = mock(InteractionCommand.class);
		try {
			when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.FAILURE);
		} catch(InteractionException ie) {
			Assert.fail(ie.getMessage());
		}
		return mockCommand;
	}

	/*
	 * This test is for an OPTIONS request.
	 * A OPTIONS request uses a GET command, the response must include an Allow header
	 * and no body plus HttpStatus 204 "No Content".
	 */
	@Test
	public void testOPTIONSBuildResponseWithNoContent() throws Exception {
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
		initialState.addTransition(new Transition.Builder().method(HttpMethod.GET).target(initialState).build());
		/*
		 * Construct an InteractionCommand that simply mocks the result of 
		 * a successful command.
		 */
		final InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) {
				ctx.setResource(new EntityResource<Object>(null));
				return Result.SUCCESS;
			}
		};
		
		// create mock command controller
		MapBasedCommandController mockCommandController = new MapBasedCommandController();
		mockCommandController.setCommandMap(new HashMap<String, InteractionCommand>(){
            private static final long serialVersionUID = 1L;
            {
		        put("GET", mockCommand);
		        put("DO", mockCommand);
		    }
		});

		// RIM with command controller that issues our mock InteractionCommand
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState), createMockMetadata());
		Response response = rim.options(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
		
		// 204 http status for no content
		assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
		// check Allow header
		Object allow = response.getMetadata().getFirst("Allow");
        assertNotNull(allow);
        String[] allows = allow.toString().split(", ");
		assertEquals(3, allows.length);
		List<String> allowsList = Arrays.asList(allows);
        assertTrue(allowsList.contains("GET"));
        assertTrue(allowsList.contains("OPTIONS"));
        assertTrue(allowsList.contains("HEAD"));
	}

	/*
	 * This test checks that a 503 error returns a correct response
	 */
	@Test
	public void testBuildResponseWith503ServiceUnavailable() {
		Response response = getMockResponse(
				getInteractionExceptionMockCommand(Status.SERVICE_UNAVAILABLE, "Failed to connect to resource manager."),
				new GETExceptionCommand()
			);
		assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Excepted a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, GenericError.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			GenericError error = (GenericError)er.getEntity();
			assertEquals("503", error.getCode());
			assertEquals("Failed to connect to resource manager.", error.getMessage());
		}
		else {
			fail("Response body is not a generic error entity resource type.");
		}
	}

	/*
	 * This test checks returning a 503 error without a response body
	 */
	@Test
	public void testBuildResponseWith503ServiceUnavailableWithoutResponseBody() {
		Response response = getMockResponse(getInteractionExceptionMockCommand(Status.SERVICE_UNAVAILABLE, null));
		assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
		
		assertNull(response.getEntity());
	}
	
	/*
	 * This test checks that a 504 error returns a correct response
	 */
	@Test
	public void testBuildResponseWith504GatewayTimeout() {
		Response response = getMockResponse(
				getInteractionExceptionMockCommand(HttpStatusTypes.GATEWAY_TIMEOUT, "Request timeout."),
				new GETExceptionCommand()
			);
		assertEquals(HttpStatusTypes.GATEWAY_TIMEOUT.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Excepted a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, GenericError.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			GenericError error = (GenericError)er.getEntity();
			assertEquals("504", error.getCode());
			assertEquals("Request timeout.", error.getMessage());
		}
		else {
			fail("Response body is not a generic error entity resource type.");
		}
	}
	
	/*
	 * This test checks that a 500 error returns a proper error message inside
	 * the body of the response.
	 */
	@Test
	public void testBuildResponseWith500InternalServerError() {
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState createPsuedoState = new ResourceState(initialState, "create", mockActions());
		// create new machine
		initialState.addTransition(new Transition.Builder().method("POST").target(createPsuedoState).build());
		
		Map<String,InteractionCommand> commands = new HashMap<String,InteractionCommand>();
		commands.put("DO", getGenericErrorMockCommand(Result.FAILURE, "Resource manager: 5 fatal error and 2 warnings."));
		commands.put("GET", mockCommand_SUCCESS());
		CommandController commandController = new MapBasedCommandController(commands);
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, new ResourceStateMachine(initialState, new BeanTransformer()), createMockMetadata());
		Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mockEntityResourceWithId("123"));

		assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Excepted a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, GenericError.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			GenericError error = (GenericError)er.getEntity();
			assertEquals("FAILURE", error.getCode());
			assertEquals("Resource manager: 5 fatal error and 2 warnings.", error.getMessage());
		}
		else {
			fail("Response body is not a generic error entity resource type.");
		}
	}

	/*
	 * This test checks that a 400 error returns a proper error message inside
	 * the body of the response.
	 */
	@Test
	public void testBuildResponseWith400BadRequest() {
		Response response = getMockResponse(getGenericErrorMockCommand(Result.INVALID_REQUEST, "Resource manager: 4 validation errors."));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Excepted a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, GenericError.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			GenericError error = (GenericError)er.getEntity();
			assertEquals("INVALID_REQUEST", error.getCode());
			assertEquals("Resource manager: 4 validation errors.", error.getMessage());
		}
		else {
			fail("Response body is not a generic error entity resource type.");
		}
	}

	/*
	 * This test checks that a 403 error returns a proper status code
	 */
	@Test
	public void testBuildResponseWith403AuthorisationFailure() {
		Response response = getMockResponse(
				getInteractionExceptionMockCommand(Status.FORBIDDEN, "User is not allowed to access this resource."),
				new GETExceptionCommand()
			);
		assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Excepted a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, GenericError.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			GenericError error = (GenericError)er.getEntity();
			assertEquals("403", error.getCode());
			assertEquals("User is not allowed to access this resource.", error.getMessage());
		}
		else {
			fail("Response body is not a generic error entity resource type.");
		}
	}

	/*
	 * This test checks that a 500 error is returned when a
	 * command throws an exception.
	 */
	@Test
	public void testGETCommandThrowsException() {
		try {
			getMockResponse(getRuntimeExceptionMockCommand("Unknown fatal error."));
			fail("Test failed to throw a runtime exception");
		}
		catch(RuntimeException re) {
			assertEquals("Unknown fatal error.", re.getMessage());
		}
	}
	
	/*
	 * Test to ensure command can cause 404 error if a specific entity is not available.
	 */
	@Test
	public void testGETBuildResponseWith404NotFound() {
		Response response = getMockResponse(mockCommand_FAILURE());
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
	}
	
	/*
	 * Test to ensure command can cause 404 error without a response body if a specific entity is not available.
	 */
	@Test
	public void testGETBuildResponseWith404NotFoundWithoutResponseBody() {
		Response response = getMockResponse(mockCommand_FAILURE());
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
		
		assertNull(response.getEntity());
	}

	/*
	 * Test to ensure command can cause 404 error if a specific entity is not available.
	 */
	@Test
	public void testGETBuildResponseWith404NotFoundInteractionException() {
		Response response = getMockResponse(
				getInteractionExceptionMockCommand(Status.NOT_FOUND, "Resource manager: entity Fred not found or currently unavailable."),
				new GETExceptionCommand()
			);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Excepted a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, GenericError.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			GenericError error = (GenericError)er.getEntity();
			assertEquals("404", error.getCode());
			assertEquals("Resource manager: entity Fred not found or currently unavailable.", error.getMessage());
		}
		else {
			fail("Response body is not a generic error entity resource type.");
		}
	}

	/*
	 * Test to ensure command can cause 404 error if a specific entity is not available on DELETE.
	 */
	@Test
	public void testDELETEBuildResponseWith404NotFound() {
		List<Action> actions = new ArrayList<Action>();
		actions.add(new Action("DO", Action.TYPE.ENTRY));
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * creating a resource
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState individualMachine = new ResourceState(initialState, "machine", actions, "/{id}");
		
		// create new machine
		initialState.addTransition(new Transition.Builder().method("DELETE").target(individualMachine).build());
		
		// RIM with command controller that issues commands that return SUCCESS for 'DO' action and FAILURE for 'GET' action (see mockActions())
		Map<String,InteractionCommand> commands = new HashMap<String,InteractionCommand>();
		commands.put("DO", mockCommand_FAILURE());
		commands.put("GET", mockCommand_SUCCESS());
		CommandController commandController = new MapBasedCommandController(commands);
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, new ResourceStateMachine(initialState, new BeanTransformer()), createMockMetadata());

		HTTPHypermediaRIM deleteInteraction = (HTTPHypermediaRIM) rim.getChildren().iterator().next();
		Response response = deleteInteraction.delete(mock(HttpHeaders.class), "id", mockEmptyUriInfo());

		assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
	}

	/*
	 * This test checks that a 400 error returns a proper error message inside
	 * the body of the response.
	 */
	@Test
	public void testBuildResponseWith400BadRequestFromErrorResource() {
		Response response = getMockResponseWithErrorResource(getGenericErrorMockCommand(Result.INVALID_REQUEST, "Resource manager: 4 validation errors."));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Excepted a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, GenericError.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			assertEquals("ErrorEntity", er.getEntityName());
			GenericError error = (GenericError)er.getEntity();
			assertEquals("INVALID_REQUEST", error.getCode());
			assertEquals("Resource manager: 4 validation errors.", error.getMessage());
			assertNotNull(er.getLinks());
			assertFalse(er.getLinks().isEmpty());
			assertEquals(1, er.getLinks().size());
			Link link = (Link) er.getLinks().toArray()[0];
			assertEquals("self", link.getRel());
		}
		else {
			fail("Response body is not a generic error entity resource type.");
		}
	}

	@Test
	public void testGETWithETagHeader() throws InteractionException {
		//Create mock command
		InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) throws InteractionException {
				RESTResource resource = new EntityResource<Object>(null);
				resource.setEntityTag("ABCDEFG");
				ctx.setResource(resource);
				return Result.SUCCESS;
			}
		};

		//Process mock command and check the response 
		Response response = getMockResponse(mockCommand);
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		List<Object> etagHeader = response.getMetadata().get(HttpHeaders.ETAG);
		assertNotNull(etagHeader);
        assertEquals(1, etagHeader.size());
        assertEquals("ABCDEFG", etagHeader.get(0));
	}

	@Test
	public void testGETWithoutETagHeader() throws InteractionException {
		//Create mock command
		InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) throws InteractionException {
				ctx.setResource(new EntityResource<Object>(null));
				return Result.SUCCESS;
			}
		};

		//Process mock command and check the response 
		Response response = getMockResponse(mockCommand);
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		List<Object> etagHeader = response.getMetadata().get(HttpHeaders.ETAG);
		assertNull(etagHeader);
	}

	@Test
	public void testGETWithEmptyETagHeader() throws InteractionException {
		//Create mock command
		InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) throws InteractionException {
				RESTResource resource = new EntityResource<Object>(null);
				resource.setEntityTag("");
				ctx.setResource(resource);
				return Result.SUCCESS;
			}
		};

		//Process mock command and check the response 
		Response response = getMockResponse(mockCommand);
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		List<Object> etagHeader = response.getMetadata().get(HttpHeaders.ETAG);
		assertNull(etagHeader);
	}
	
	/*
	 * This test checks that a 412 error returns a proper error message inside
	 * the body of the response.
	 */
	@Test
	public void testBuildResponseWith412PreconditionFailed() {
		Response response = getMockResponse(getGenericErrorMockCommand(Result.CONFLICT, "Resource has been modified by somebody else."));
		assertEquals(Response.Status.PRECONDITION_FAILED.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Excepted a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, GenericError.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			GenericError error = (GenericError)er.getEntity();
			assertEquals("CONFLICT", error.getCode());
			assertEquals("Resource has been modified by somebody else.", error.getMessage());
		}
		else {
			fail("Response body is not a generic error entity resource type.");
		}
	}

	/*
	 * Test to ensure we return a 304 Not modified if the etag of the response is the same
	 * as the etag on the request's If-None-Match header.
	 */
	@Test
	public void testBuildResponseWith304NotModified() {
		HttpHeaders httpHeaders = mock(HttpHeaders.class);
		doAnswer(new Answer<List<String>>() {
			@Override
	        public List<String> answer(InvocationOnMock invocation) throws Throwable {
	        	String headerName = (String) invocation.getArguments()[0];
	        	if(headerName.equals(HttpHeaders.IF_NONE_MATCH)) {
	        		return new ArrayList<String>() {
                        private static final long serialVersionUID = 1L;
                        {
    	        		    add("ABCDEFG");
    	        		}
                    };
	        	}
	            return null;
	        }
	    }).when(httpHeaders).getRequestHeader(any(String.class));				
		
		Response response = getMockResponse(getEntityMockCommand("TestEntity", new EntityProperties(), "ABCDEFG"), null, httpHeaders);
		assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNull("Should not have a response body", ge);
	}
	
	/*
	 * Test to ensure we return a 200 Success if the etag of the response is not the same
	 * as the etag on the request's If-None-Match header.
	 */
	@Test
	public void testBuildResponseGETModifiedResource() {
		HttpHeaders httpHeaders = mock(HttpHeaders.class);
		doAnswer(new Answer<List<String>>() {
			@Override
	        public List<String> answer(InvocationOnMock invocation) throws Throwable {
	        	String headerName = (String) invocation.getArguments()[0];
	        	if(headerName.equals(HttpHeaders.IF_NONE_MATCH)) {
	        		return new ArrayList<String>() {
                        private static final long serialVersionUID = 1L;
                        {
	        		    add("ABCDEFG");
	        		    }
        		    };
	        	}
	            return null;
	        }
	    }).when(httpHeaders).getRequestHeader(any(String.class));				
		
		Response response = getMockResponse(getEntityMockCommand("TestEntity", new EntityProperties(), "IJKLMNO"), null, httpHeaders);
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		
		GenericEntity<?> ge = (GenericEntity<?>) response.getEntity();
		assertNotNull("Expected a response body", ge);
		if(ResourceTypeHelper.isType(ge.getRawType(), ge.getType(), EntityResource.class, Entity.class)) {
			EntityResource<?> er = (EntityResource<?>) ge.getEntity();
			assertEquals("TestEntity", ((Entity)er.getEntity()).getName());
			assertEquals("IJKLMNO", er.getEntityTag());		//Response should have new etag
		}
		else {
			fail("Response body is not an entity resource type.");
		}
	}
	
	/*
	 * This test is for a POST request that creates a new resource, and returns
	 * the links for the resource we auto transition to.
	 */
	@Test
	public void testPOSTwithConditionalAutoTransitions() throws Exception {
		/*
		 * construct an InteractionContext that simply mocks the result of 
		 * creating a resource
		 */
		ResourceState initialState = new ResourceState("home", "initial", mockActions(), "/machines");
		ResourceState createPsuedoState = new ResourceState(initialState, "create", mockActions());
		ResourceState individualMachine = new ResourceState(initialState, "machine", mockActions(), "/individualMachine1/{id}");
		individualMachine.addTransition(new Transition.Builder().method("GET").target(initialState).build());
		ResourceState individualMachine2 = new ResourceState(initialState, "machine2", mockActions(), "/individualMachine2/{id}");
		individualMachine.addTransition(new Transition.Builder().method("GET").target(initialState).build());
		
		// create new machine
		initialState.addTransition(new Transition.Builder().method("POST").target(createPsuedoState).build());
		// an auto transition to the new resource
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		uriLinkageMap.put("id", "{id}");

		//Add the first auto-transition
		List<Expression> conditionalExpressions1 = new ArrayList<Expression>();
		conditionalExpressions1.add(new SimpleLogicalExpressionEvaluator(new ArrayList<Expression>()) {
			@Override
			public boolean evaluate(HTTPHypermediaRIM rimHandler, InteractionContext ctx, EntityResource<?> resource) {
				return false;
			}
			
		});
		createPsuedoState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(individualMachine).uriParameters(uriLinkageMap).evaluation(new SimpleLogicalExpressionEvaluator(conditionalExpressions1)).build());

		//Add a second auto-transition
		List<Expression> conditionalExpressions2 = new ArrayList<Expression>();
		conditionalExpressions2.add(new SimpleLogicalExpressionEvaluator(new ArrayList<Expression>()) {
			@Override
			public boolean evaluate(HTTPHypermediaRIM rimHandler, InteractionContext ctx, EntityResource<?> resource) {
				return true;
			}
			
		});
		createPsuedoState.addTransition(new Transition.Builder().flags(Transition.AUTO).target(individualMachine2).uriParameters(uriLinkageMap).evaluation(new SimpleLogicalExpressionEvaluator(conditionalExpressions2)).build());
		
		// RIM with command controller that issues commands that always return SUCCESS
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockNoopCommandController(), new ResourceStateMachine(initialState, new BeanTransformer()), createMockMetadata());
		Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mockEntityResourceWithId("123"));

        // the response entity must not be null and must be an instance of/subtype RESTResource} 
        assertNotNull(GenericEntity.class.isAssignableFrom(response.getEntity().getClass()));
        GenericEntity<?> responseEntity = (GenericEntity<?>)response.getEntity();
        assertTrue(RESTResource.class.isAssignableFrom(responseEntity.getEntity().getClass()));
		/*
		 *  Assert the links in the response match the target resource
		 */
		EntityResource<?> createdResource = (EntityResource<?>) ((GenericEntity<?>)response.getEntity()).getEntity();
		List<Link> links = new ArrayList<Link>(createdResource.getLinks());
		assertEquals(1, links.size());
		assertEquals("machine2", links.get(0).getTitle());
		assertEquals("/baseuri/machines/individualMachine2/123", links.get(0).getHref());
	}
	
	protected Response getMockResponse(InteractionCommand mockCommand) {
		return this.getMockResponse(mockCommand, null);
	}

	protected Response getMockResponse(InteractionCommand mockCommand, InteractionCommand mockExceptionCommand) {
		return this.getMockResponse(mockCommand, mockExceptionCommand, mock(HttpHeaders.class));
	}
	
	protected Response getMockResponse(final InteractionCommand mockCommand, final InteractionCommand mockExceptionCommand, HttpHeaders httpHeaders) {
		MapBasedCommandController mockCommandController = mock(MapBasedCommandController.class);
		mockCommandController.setCommandMap(new HashMap<String, InteractionCommand>(){
            private static final long serialVersionUID = 1L;

            {
		        put("GET", mockCommand);
		        if(mockExceptionCommand != null){
		            put("GETException", mockExceptionCommand);
		        }
		    }
		});

		when(mockCommandController.fetchCommand("GET")).thenReturn(mockCommand);
		when(mockCommandController.fetchCommand("DO")).thenReturn(mockCommand);
		if(mockExceptionCommand != null) {
			when(mockCommandController.fetchCommand("GETException")).thenReturn(mockExceptionCommand);
		}

		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
		initialState.setInitial(true);
		ResourceState exceptionState = null;
		if(mockExceptionCommand != null) {
			exceptionState = new ResourceState("exception", "exceptionState", mockExceptionActions(), "/exception");
			exceptionState.setException(true);
		}
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState, exceptionState), createMockMetadata());
		return rim.get(httpHeaders, "id", mockEmptyUriInfo());
	}

	protected Response getMockResponseWithErrorResource(final InteractionCommand mockCommand) {
		MapBasedCommandController mockCommandController = mock(MapBasedCommandController.class);
		final InteractionCommand noopCommand = new NoopGETCommand();
		mockCommandController.setCommandMap(new HashMap<String, InteractionCommand>(){
            private static final long serialVersionUID = 1L;
            {
		        put("GET", mockCommand);
		        put("noop", noopCommand);
		    }
		});
		when(mockCommandController.fetchCommand("GET")).thenReturn(mockCommand);
		when(mockCommandController.fetchCommand("DO")).thenReturn(mockCommand);
		when(mockCommandController.fetchCommand("noop")).thenReturn(noopCommand);
		ResourceState errorState = new ResourceState("ErrorEntity", "errorState", mockErrorActions(), "/error");
		ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path", null, null, errorState);
		initialState.setInitial(true);
		HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState), createMockMetadata());
		return rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
	}
	
	protected InteractionCommand getGenericErrorMockCommand(final InteractionCommand.Result result, final String body) {
		InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) {
				if(body != null) {
					ctx.setResource(createGenericErrorResource(new GenericError(result.toString(), body)));
				}
				return result;
			}
		};
		return mockCommand;
	}

	protected InteractionCommand getInteractionExceptionMockCommand(final StatusType status, final String message) {
		InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) throws InteractionException {
				throw new InteractionException(status, message);
			}
		};
		return mockCommand;
	}
	
	protected InteractionCommand getRuntimeExceptionMockCommand(final String errorMessage) {
		InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) {
				throw new RuntimeException(errorMessage);
			}
		};
		return mockCommand;
	}

	protected InteractionCommand getEntityMockCommand(final String entityName, final EntityProperties entityProperties, final String etag) {
		InteractionCommand mockCommand = new InteractionCommand() {
			@Override
			public Result execute(InteractionContext ctx) {
				RESTResource resource = CommandHelper.createEntityResource(new Entity(entityName, entityProperties));
				resource.setEntityTag(etag);
				ctx.setResource(resource);
				return Result.SUCCESS;
			}
		};
		return mockCommand;
	}
	
	public static EntityResource<GenericError> createGenericErrorResource(GenericError error) 
	{
		return CommandHelper.createEntityResource(error, GenericError.class);
	}	
}
