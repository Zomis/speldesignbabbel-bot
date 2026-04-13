resource "aws_iam_role" "scheduler_role" {
  name        = "speldesignbabbel-scheduler-role"
  description = "Speldesignbabbel Bot Lambda IAM Role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "scheduler.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "scheduler_invoke_lambda" {
  name = "scheduler-invoke-lambda"
  role = aws_iam_role.scheduler_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "lambda:InvokeFunction"
      Resource = aws_lambda_function.speldesignbabbel_lambda.arn
    }]
  })
}

resource "aws_scheduler_schedule_group" "speldesignbabbel_tasks" {
  name = "speldesignbabbel-schedule"
}

resource "aws_scheduler_schedule" "week_update" {
  group_name          = aws_scheduler_schedule_group.speldesignbabbel_tasks.name
  name                = "discord-post"
  schedule_expression = "cron(0 6 ? * MON *)"
  description         = "Make Discord post"
  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn = aws_lambda_function.speldesignbabbel_lambda.arn
    role_arn = aws_iam_role.scheduler_role.arn
    input = jsonencode({})
  }
}
