resource "aws_instance" "nodejs-app" {
  ami                    = "ami-016485166ec7fa705" # Ubuntu server 22.04
  instance_type          = "t4g.small" # Do not forget to turn off the instance after the test is complete ( around $30 monthly)
  vpc_security_group_ids = [aws_security_group.web-sg.id]
  key_name               = "TestInstance2Last"
  tags = {
    Name = "Node_JS_app"
  }
}