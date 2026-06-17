$extensions = @("*.kt", "*.java", "*.xml")
$targetDir = "$PSScriptRoot\app\src"
$count = 0

function Strip-KotlinComments($code) {
    $out = New-Object System.Text.StringBuilder
    $i = 0
    $len = $code.Length
    $state = "CODE"  # CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, RAW_STRING, CHAR

    while ($i -lt $len) {
        $ch = $code[$i]

        if ($state -eq "CODE") {
            if ($ch -eq '/' -and $i + 1 -lt $len) {
                $next = $code[$i + 1]
                if ($next -eq '/') {
                    $state = "LINE_COMMENT"
                    $i += 2
                    continue
                } elseif ($next -eq '*') {
                    $state = "BLOCK_COMMENT"
                    $i += 2
                    continue
                }
            } elseif ($ch -eq '"' -and $i + 2 -lt $len) {
                if ($code[$i + 1] -eq '"' -and $code[$i + 2] -eq '"') {
                    $state = "RAW_STRING"
                    [void]$out.Append('"""')
                    $i += 3
                    continue
                }
            } elseif ($ch -eq '"') {
                $state = "STRING"
                [void]$out.Append('"')
                $i += 1
                continue
            } elseif ($ch -eq "'") {
                $state = "CHAR"
                [void]$out.Append("'")
                $i += 1
                continue
            }
            [void]$out.Append($ch)
            $i += 1

        } elseif ($state -eq "LINE_COMMENT") {
            if ($ch -eq "`r" -or $ch -eq "`n") {
                $state = "CODE"
                [void]$out.Append($ch)
            }
            $i += 1

        } elseif ($state -eq "BLOCK_COMMENT") {
            if ($ch -eq '*' -and $i + 1 -lt $len -and $code[$i + 1] -eq '/') {
                $state = "CODE"
                $i += 2
            } else {
                $i += 1
            }

        } elseif ($state -eq "STRING") {
            if ($ch -eq '\' -and $i + 1 -lt $len) {
                [void]$out.Append($ch)
                $i += 1
                [void]$out.Append($code[$i])
                $i += 1
            } elseif ($ch -eq '"') {
                $state = "CODE"
                [void]$out.Append('"')
                $i += 1
            } else {
                [void]$out.Append($ch)
                $i += 1
            }

        } elseif ($state -eq "RAW_STRING") {
            if ($ch -eq '"' -and $i + 2 -lt $len -and $code[$i + 1] -eq '"' -and $code[$i + 2] -eq '"') {
                $state = "CODE"
                [void]$out.Append('"""')
                $i += 3
            } else {
                [void]$out.Append($ch)
                $i += 1
            }

        } elseif ($state -eq "CHAR") {
            if ($ch -eq '\' -and $i + 1 -lt $len) {
                [void]$out.Append($ch)
                $i += 1
                [void]$out.Append($code[$i])
                $i += 1
            } elseif ($ch -eq "'") {
                $state = "CODE"
                [void]$out.Append("'")
                $i += 1
            } else {
                [void]$out.Append($ch)
                $i += 1
            }
        }
    }
    return $out.ToString()
}

Get-ChildItem $targetDir -Recurse -Include $extensions -File | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $original = $content
    $ext = $_.Extension.ToLower()

    if ($ext -eq ".xml") {
        $content = $content -replace '<!--[\s\S]*?-->', ''
    } elseif ($ext -in @(".kt", ".java")) {
        $content = Strip-KotlinComments $content
    }

    if ($content -ne $original) {
        $content = $content.TrimEnd() + "`r`n"
        [System.IO.File]::WriteAllText($_.FullName, $content, [System.Text.UTF8Encoding]::new($false))
        $count++
    }
}

Write-Host "Stripped comments from $count files."
