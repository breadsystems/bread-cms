import React from 'react';
import styled from 'styled-components';

import {Box} from './Box';

function MediaLibrary({settings}) {
  console.log('settings', settings);
  return <Box>
    <h2>MEDIAZ GO HERE</h2>
    <section>Section one</section>
    <section>Section two</section>
  </Box>;
}

export {MediaLibrary};
