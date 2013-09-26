package com.temenos.interaction.core.hypermedia.expression;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import com.temenos.interaction.core.command.CommandFailureException;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.RESTResource;

public class ResourceGETExpression implements Expression {

	public enum Function {
		OK,
		NOT_FOUND
	}
	
	public final Function function;
	public final String state;
	
	public ResourceGETExpression(String state, Function function) {
		this.function = function;
		this.state = state;
	}
	
	public Function getFunction() {
		return function;
	}

	public String getState() {
		return state;
	}
	
	@Override
	public boolean evaluate(ResourceStateMachine hypermediaEngine, InteractionContext ctx) {
		ResourceState target = hypermediaEngine.getResourceStateByName(state);
		if (target == null)
			throw new IllegalArgumentException("Indicates a problem with the RIM, it allowed an invalid state to be supplied");
		assert(target.getActions() != null);
		assert(target.getActions().size() == 1);
		
		//Create a new interaction context for this state
    	Transition transition = ctx.getCurrentState().getTransition(target);
    	MultivaluedMap<String, String> pathParameters = getPathParametersForTargetState(hypermediaEngine, ctx, transition);
    	InteractionContext newCtx = new InteractionContext(ctx, pathParameters, null, target);

    	//Get the target resource
		try {
			hypermediaEngine.getResource(target, newCtx, false);	//Ignore the resource and its links, just interested in the result status
			if(getFunction().equals(Function.OK)) {
				return true;
			}
		}
		catch(CommandFailureException cfe) {
			if(getFunction().equals(Function.NOT_FOUND)) {
				return true;
			}
		}
		catch(InteractionException ie) {
			if(getFunction().equals(Function.NOT_FOUND)) {
				return true;
			}
		}
		return false;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		if (getFunction().equals(ResourceGETExpression.Function.OK))
			sb.append("OK(").append(getState()).append(")");
		if (getFunction().equals(ResourceGETExpression.Function.NOT_FOUND))
			sb.append("NOT_FOUND").append(getState()).append(")");
		return sb.toString();
	}
	
	/*
	 * Obtain path parameters to use when accessing
	 * a resource state on an expression.  
	 */
	@SuppressWarnings("unchecked")
	private MultivaluedMap<String, String> getPathParametersForTargetState(ResourceStateMachine hypermediaEngine, InteractionContext ctx, Transition transition) {
		//If available (for performance), read transition properties from the context
    	Map<String, Object> transitionProperties = new HashMap<String, Object>();
    	Object transPropsAttr = ctx.getAttribute(ResourceStateMachine.TRANSITION_PROPERTIES_CTX_ATTRIBUTE);
    	if(transPropsAttr != null) {
    		transitionProperties = (Map<String, Object>) transPropsAttr;
    	}
    	else {
    		//Otherwise, re-evaluate transition properties 
    		RESTResource resource = ctx.getResource();
    		if(resource != null && resource instanceof EntityResource) {
    			Object entity = ((EntityResource<?>) resource).getEntity();
    	    	transitionProperties = hypermediaEngine.getTransitionProperties(transition, entity, null); 
    		}    		
    	}
    	
    	//apply transition properties to path parameters 
    	return hypermediaEngine.getPathParametersForTargetState(transition, transitionProperties);
	}
}