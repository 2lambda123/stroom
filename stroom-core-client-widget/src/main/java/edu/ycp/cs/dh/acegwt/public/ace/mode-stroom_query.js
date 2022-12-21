define("ace/mode/stroom_query_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

var StroomQueryHighlightRules = function() {

    var keywords = (
        "select|insert|update|delete|from|where|and|or|group|by|order|limit|offset|having|as|case|" +
        "when|then|else|end|type|left|right|join|on|outer|desc|asc|union|create|table|primary|key|if|" +
        "foreign|not|references|default|null|inner|cross|natural|database|drop|grant"
    );

    var builtinConstants = (
        "true|false"
    );

    var builtinFunctions = (
        "avg|count|first|last|max|min|sum|ucase|lcase|mid|len|round|rank|now|format|" + 
        "coalesce|ifnull|isnull|nvl"
    );

    var dataTypes = (
        "int|numeric|decimal|date|varchar|char|bigint|float|double|bit|binary|text|set|timestamp|" +
        "money|real|number|integer"
    );

    var keywordMapper = this.createKeywordMapper({
        "support.function": builtinFunctions,
        "keyword": keywords,
        "constant.language": builtinConstants,
        "storage.type": dataTypes
    }, "identifier", true);

    this.$rules = {
        "start" : [
        {
            token : "comment",   // single comment
            regex : /(\/\/.*)/,
        },
        {
            token : "comment",   // block comment
            start : "/\\*",
            end : "\\*/"
        },
        {
            token : "list",   // pipe
            regex : /\|/
        },
        {
            token : "string",           // " string
//            regex : '".*?"'
            regex : /"(?:[^"\\]|\\.)*"/
        },
        {
            token : "string",           // ' string
//            regex : "'.*?'"
            regex : /'(?:[^'\\]|\\.)*'/
//        }, {
//            token : "string",           // ` string (apache drill)
//            regex : "`.*?`"
//        {
//            token : "comment",
//            regex : "--.*$"
//        },

        },
//        {
//            token : "constant.numeric", // float
//            regex : "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
//        },
        {
            token : keywordMapper,
            regex : "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
        }, {
            token : "keyword.operator",
            regex : "\\+|\\-|\\/|\\/\\/|%|<@>|@>|<@|&|\\^|~|<|>|<=|=>|==|!=|<>|="
        }, {
            token : "paren.lparen",
            regex : "[\\(]"
        }, {
            token : "paren.rparen",
            regex : "[\\)]"
        },
        {
            token : "text",
            regex : /\|/
        },
        {
            token : "text",
            regex : /,/
        },
//        {
//            token : "string",
//            regex : ".*"
//        }
         ]
    };
    this.normalizeRules();
};

oop.inherits(StroomQueryHighlightRules, TextHighlightRules);

exports.StroomQueryHighlightRules = StroomQueryHighlightRules;
});

define("ace/mode/stroom_query",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/stroom_query_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var StroomQueryHighlightRules = require("./stroom_query_highlight_rules").StroomQueryHighlightRules;

var Mode = function() {
    this.HighlightRules = StroomQueryHighlightRules;
    this.$behaviour = this.$defaultBehaviour;
};
oop.inherits(Mode, TextMode);

(function() {

//    this.lineCommentStart = "--";

    this.$id = "ace/mode/stroom_query";
//    this.snippetFileId = "ace/snippets/stroom_query";
}).call(Mode.prototype);

exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/stroom_query"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            