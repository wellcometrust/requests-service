locals {
  status_listener_port   = "65535"
  requests_listener_port = "65534"

  api_security_groups = [
    "${aws_security_group.service_egress_security_group.id}",
    "${aws_security_group.service_lb_ingress_security_group.id}",
  ]

  context_url = "https://api.wellcomecollection.org/stacks/v1/context.json"
}

data "aws_vpc" "vpc" {
  id = "${var.vpc_id}"
}