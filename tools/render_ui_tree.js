/**
 * render_ui_tree.js
 * 
 * Virtual DOM / UI Tree Renderer for OmniTV Android Layouts.
 * Simulates React-like component rendering of Android TV layouts in JSON format
 * across multiple locales ('en' and 'es'), without requiring an emulator or physical Chromecast.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const STRINGS_EN_PATH = path.join(ROOT, 'app/src/main/res/values/strings.xml');
const STRINGS_ES_PATH = path.join(ROOT, 'app/src/main/res/values-es/strings.xml');
const QUICK_MENU_XML = path.join(ROOT, 'app/src/main/res/layout/activity_quick_menu.xml');
const AI_SUMMARY_XML = path.join(ROOT, 'app/src/main/res/layout/view_ai_summary_overlay.xml');

// 1. Simple XML String Dictionary Parser
function parseStringsXml(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    const strings = {};
    const stringRegex = /<string\s+name="([^"]+)">([\s\S]*?)<\/string>/g;
    let match;
    while ((match = stringRegex.exec(content)) !== null) {
        let val = match[2].trim()
            .replace(/\\'/g, "'")
            .replace(/\\"/g, '"')
            .replace(/\\n/g, '\n')
            .replace(/&amp;/g, '&')
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>');
        strings[match[1]] = val;
    }

    const arrayRegex = /<string-array\s+name="([^"]+)">([\s\S]*?)<\/string-array>/g;
    while ((match = arrayRegex.exec(content)) !== null) {
        const name = match[1];
        const inner = match[2];
        const items = [];
        const itemRegex = /<item>([\s\S]*?)<\/item>/g;
        let itemMatch;
        while ((itemMatch = itemRegex.exec(inner)) !== null) {
            items.push(itemMatch[1].trim()
                .replace(/\\'/g, "'")
                .replace(/\\"/g, '"')
                .replace(/\\n/g, '\n')
                .replace(/&amp;/g, '&'));
        }
        strings[`@array/${name}`] = items;
    }

    return strings;
}

// 2. Parse XML Layout to Virtual DOM Tree
function parseXmlToVdom(xmlContent, stringsDict, locale) {
    // Basic XML tree builder
    // Extract elements with id, text, hint, visibility
    const elements = [];
    const tagRegex = /<([a-zA-Z0-9_\.]+)((?:[\s\S]*?)(?:\/?>|>([\s\S]*?)<\/\1>))/g;

    function cleanAttr(attrsStr, attrName) {
        const r = new RegExp(`android:${attrName}="([^"]+)"`);
        const m = attrsStr.match(r);
        return m ? m[1] : null;
    }

    function resolveText(rawText) {
        if (!rawText) return null;
        if (rawText.startsWith('@string/')) {
            const key = rawText.replace('@string/', '');
            return stringsDict[key] || `[MISSING: ${key}]`;
        }
        return rawText;
    }

    function parseNode(tag, attrsStr, childrenStr) {
        const id = cleanAttr(attrsStr, 'id');
        const rawText = cleanAttr(attrsStr, 'text');
        const visibility = cleanAttr(attrsStr, 'visibility') || 'visible';
        const orientation = cleanAttr(attrsStr, 'orientation') || 'horizontal';

        const node = {
            component: tag.includes('.') ? tag.split('.').pop() : tag,
            id: id ? id.replace('@+id/', '') : null,
            props: {}
        };

        if (visibility !== 'visible') node.props.visibility = visibility;
        if (tag.includes('LinearLayout')) node.props.orientation = orientation;

        const resolvedText = resolveText(rawText);
        if (resolvedText) {
            node.props.text = resolvedText;
            if (rawText.startsWith('@string/')) {
                node.props.stringKey = rawText.replace('@string/', '');
            } else {
                node.props.hardcoded = true;
            }
        }

        // Recursively parse children if any
        if (childrenStr && childrenStr.trim()) {
            const children = [];
            const childRegex = /<([a-zA-Z0-9_\.]+)([\s\S]*?)(\/>|>([\s\S]*?)<\/\1>)/g;
            let cMatch;
            while ((cMatch = childRegex.exec(childrenStr)) !== null) {
                const cTag = cMatch[1];
                const cAttrs = cMatch[2];
                const cInner = cMatch[4] || '';
                children.push(parseNode(cTag, cAttrs, cInner));
            }
            if (children.length > 0) {
                node.children = children;
            }
        }

        return node;
    }

    const rootRegex = /<([a-zA-Z0-9_\.]+)([\s\S]*?)>([\s\S]*)<\/\1>/;
    const rootMatch = xmlContent.match(rootRegex);
    if (!rootMatch) return null;

    return parseNode(rootMatch[1], rootMatch[2], rootMatch[3]);
}

// 3. Extract Screens / Subpanels from Layout
function extractScreens(vdomRoot) {
    const screens = {};

    function walk(node) {
        if (!node) return;
        if (node.id && (node.id.startsWith('panel_') || node.id === 'ai_panel_drawer' || node.id === 'quick_menu_container' || node.id === 'dialog_container')) {
            screens[node.id] = node;
        }
        if (node.children) {
            node.children.forEach(walk);
        }
    }

    walk(vdomRoot);
    return screens;
}

// 4. Spanish Words Detector (checks if English rendered UI has leftover Spanish)
const SPANISH_INDICATORS = [
    /\bconfigurar\b/i,
    /\bopacidad\b/i,
    /\bparche\b/i,
    /\bprobar\b/i,
    /\blisto\b/i,
    /\bactivar\b/i,
    /\bdesactivar\b/i,
    /\bbrillo\b/i,
    /\bguardar\b/i,
    /\bcancelar\b/i,
    /\bpuntos clave\b/i,
    /\bconclusiones\b/i,
    /\bmomentos clave\b/i,
    /\bhablar\b/i,
    /\bcerrar\b/i,
    /\bvolver\b/i,
    /\btamaño\b/i,
    /\bdesplazar\b/i,
    /\bduración\b/i,
    /\bminutos\b/i,
    /\bsegundos\b/i,
    /\bhoras\b/i,
    /\bhorario\b/i,
    /\bnocturno\b/i,
    /\bprotector\b/i,
    /\binactividad\b/i,
    /\bespera consciente\b/i,
    /\btraductor\b/i,
    /\bdoblaje\b/i,
    /\bteclas\b/i
];

function findSpanishInEnglishTree(node, issues = []) {
    if (!node) return issues;
    if (node.props && node.props.text) {
        const text = node.props.text;
        for (const pattern of SPANISH_INDICATORS) {
            if (pattern.test(text)) {
                issues.push({
                    id: node.id,
                    component: node.component,
                    text: text,
                    stringKey: node.props.stringKey || 'HARDCODED_IN_XML',
                    matched: pattern.toString()
                });
                break;
            }
        }
    }
    if (node.children) {
        node.children.forEach(c => findSpanishInEnglishTree(c, issues));
    }
    return issues;
}

// Main execution
function main() {
    const stringsEn = parseStringsXml(STRINGS_EN_PATH);
    const stringsEs = parseStringsXml(STRINGS_ES_PATH);

    const quickMenuXml = fs.readFileSync(QUICK_MENU_XML, 'utf8');
    const aiSummaryXml = fs.readFileSync(AI_SUMMARY_XML, 'utf8');

    const args = process.argv.slice(2);
    const targetLocale = args[0] || 'en';
    const dict = targetLocale === 'es' ? stringsEs : stringsEn;

    const quickMenuTree = parseXmlToVdom(quickMenuXml, dict, targetLocale);
    const aiSummaryTree = parseXmlToVdom(aiSummaryXml, dict, targetLocale);

    if (args.includes('--audit')) {
        console.log(`=== AUDIT: Scanning English UI for leftover Spanish texts ===`);
        const enQuickMenu = parseXmlToVdom(quickMenuXml, stringsEn, 'en');
        const enAiSummary = parseXmlToVdom(aiSummaryXml, stringsEn, 'en');

        const quickMenuIssues = findSpanishInEnglishTree(enQuickMenu);
        const aiSummaryIssues = findSpanishInEnglishTree(enAiSummary);

        const allIssues = [...quickMenuIssues, ...aiSummaryIssues];
        console.log(`Found ${allIssues.length} leftover Spanish texts in English mode:\n`);
        allIssues.forEach((issue, idx) => {
            console.log(`${idx + 1}. [${issue.id || 'anonymous'}] (${issue.component})`);
            console.log(`   Text: "${issue.text}"`);
            console.log(`   Key: ${issue.stringKey} (Match: ${issue.matched})`);
        });
        return;
    }

    if (args.includes('--screens')) {
        const screens = {
            ...extractScreens(quickMenuTree),
            ...extractScreens(aiSummaryTree)
        };
        console.log(JSON.stringify(Object.keys(screens), null, 2));
        return;
    }

    // Default: dump simulated React-like VDOM JSON
    const screenFilter = args.find(a => !a.startsWith('--') && a !== 'en' && a !== 'es');
    if (screenFilter) {
        const screens = {
            ...extractScreens(quickMenuTree),
            ...extractScreens(aiSummaryTree)
        };
        if (screens[screenFilter]) {
            console.log(JSON.stringify(screens[screenFilter], null, 2));
        } else {
            console.log(`Screen "${screenFilter}" not found. Available screens:`, Object.keys(screens));
        }
        return;
    }

    console.log(JSON.stringify({
        locale: targetLocale,
        quickMenu: extractScreens(quickMenuTree),
        aiSummary: extractScreens(aiSummaryTree)
    }, null, 2));
}

main();
