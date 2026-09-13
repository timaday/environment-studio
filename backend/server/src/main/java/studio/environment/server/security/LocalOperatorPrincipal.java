package studio.environment.server.security;

import studio.environment.core.session.Owner;

record LocalOperatorPrincipal(String username, Owner owner) { }
