data "archive_file" "speldesignbabbel_code" {
  type        = "zip"
  source_dir = "../lambda"
  output_path = "lambda.zip"
}

resource "aws_lambda_function" "speldesignbabbel_lambda" {
  source_code_hash =  data.archive_file.speldesignbabbel_code.output_base64sha256
  filename         = "lambda.zip"

  function_name = "speldesignbabbel-bot"
  role          = aws_iam_role.lambda_role.arn
  description   = "Automatically posts updates on Discord about weekly active threads"

  runtime = "nodejs22.x"
  handler = "main.handler"

  memory_size = 512
  timeout     = 30

  environment {
    variables = {
      DISCORD_BOT_TOKEN = local.bot_token
      DISCORD_BOT_ID = local.bot_id

      OUTPUT_CHANNEL = local.output_channel
      INPUT_CHANNEL = local.input_channel
      DISCORD_SERVER = local.discord_server
    }
  }
}

resource "aws_cloudwatch_log_group" "lambda_logs" {
  name              = "/aws/lambda/${aws_lambda_function.speldesignbabbel_lambda.function_name}"
  retention_in_days = 30
}

resource "aws_iam_role" "lambda_role" {
  name        = "speldesignbabbel-lambda-role"
  description = "speldesignbabbel IAM role for Lambda"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "lambda_policy" {
  name = "speldesignbabbel-lambda-policy"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}
