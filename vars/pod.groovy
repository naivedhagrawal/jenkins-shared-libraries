def call(name='jnlp', image='jenkins/inbound-agent:latest', showRawYaml=False):
    """
    Generates a Kubernetes Pod definition as a YAML string.

    Args:
        name: The name of the container. Defaults to 'jnlp'.
        image: The Docker image to use for the container. Defaults to 'jenkins/inbound-agent:latest'.
        showRawYaml: If True, prints the raw YAML string to the console. Defaults to False.

    Returns:
        A string containing the Kubernetes Pod definition in YAML format.
    """

    yaml_string = f"""
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: {name}
      image: "{image}"
      imagePullPolicy: Always
      command:
        - cat
      tty: true
    """

    # Do NOT print the YAML string within the function
    # if showRawYaml:
    #     print(yaml_string) 

    return yaml_string

# Call the function 
yaml_string = call(showRawYaml=False) 

# Do NOT print the YAML string here either
# if yaml_string: 
#     print(yaml_string) 

# Instead, use the YAML string as needed in your Jenkins pipeline
# For example, you could use a Jenkins plugin like the Kubernetes Plugin 
# to create a Kubernetes Pod directly from the YAML string.