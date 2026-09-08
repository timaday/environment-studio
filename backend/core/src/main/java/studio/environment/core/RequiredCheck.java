package studio.environment.core;

/** Mandatory categories; each later validator must also evaluate all compiled application rules. */
public enum RequiredCheck {
    SCOPE, DEFINITION, MAPPING, VALUES, SEMANTICS, XML_FIDELITY,
    DESTINATION, CLIENT_CAPABILITY, CONTENT_POLICY, REVIEW
}
